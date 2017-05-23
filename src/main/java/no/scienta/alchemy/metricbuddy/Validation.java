package no.scienta.alchemy.metricbuddy;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class Validation {

    static <T> Class<T> vetted(Class<T> meterType) {
        if (!meterType.isInterface()) {
            throw new IllegalArgumentException("Must be an interface: " + meterType);
        }
        Stream.of(meterType.getDeclaredMethods()).forEach(Validation::vet);
        return meterType;
    }

    private static void vet(Method method) {
        Class<? extends Annotation> metricType = metricType(method);
        validators.get(metricType).apply(method);
    }

    private static Map<Class<? extends Annotation>, Function<Method, Void>> validators() {
        Map<Class<? extends Annotation>, Function<Method, Void>> map = new HashMap<>();
        map.put(Time.class, Validation::validateTimer);
        map.put(Inc.class, Validation::validateCounter);
        map.put(Histo.class, Validation::validateHistogram);
        return Collections.unmodifiableMap(map);
    }

    private static final Map<Class<? extends Annotation>, Function<Method, Void>> validators = validators();

    private static Void validateCounter(Method method) {
        if (method.getReturnType() != void.class) {
            throw new IllegalArgumentException("Counter method should return void: " + method);
        }
        if (method.getParameterCount() == 1 && method.getParameterTypes()[0] != long.class ||
                method.getParameterCount() > 1) {
            throw new IllegalArgumentException("Counter method should take no parameters or one long parameter: " + method);
        }
        return null;
    }

    private static Void validateHistogram(Method method) {
        if (method.getReturnType() != void.class) {
            throw new IllegalArgumentException("Histogram method should return void: " + method);
        }
        if (method.getParameterCount() > 1 || method.getParameterTypes()[0] != long.class) {
            throw new IllegalArgumentException("Histogram method should take one long parameter: " + method);
        }
        return null;
    }

    private static Void validateTimer(Method method) {
        if (method.getReturnType() != MetricsCollectors.Timer.class) {
            throw new IllegalArgumentException("Timer method should return " + MetricsCollectorsImpl.Timer.class + ": " + method);
        }
        if (method.getParameterCount() > 0) {
            throw new IllegalArgumentException("Timer method should take no parameters: " + method);
        }
        return null;
    }

    private static Class<? extends Annotation> metricType(Method method) {
        List<? extends Annotation> annotations = Stream.of(Inc.class, Histo.class, Time.class)
                .map(method::getAnnotation)
                .filter(Objects::nonNull)
                .map(Annotation.class::cast)
                .collect(Collectors.toList());
        if (annotations.isEmpty()) {
            if (method.getReturnType() == MetricsCollectors.Timer.class) {
                return Time.class;
            }
            return method.getDeclaringClass().getAnnotation(MetricsCollector.class).defaultMetric();
        }
        if (annotations.size() > 1) {
            throw new IllegalArgumentException("Found method with multiple annotations " + annotations + ": " + method);
        }
        return Stream.of(Inc.class, Histo.class, Time.class)
                .filter(annoType -> annoType.isInstance(annotations.get(0)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No valid annotation type: " + annotations.get(0)));
    }

    private Validation() {
    }
}
