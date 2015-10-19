package no.scienta.tools.metricbuddy;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodCall;

final class MetricsBuddy {

    static Class<? extends MetricsCollector> generateSubclass(
            Class<?> type,
            MetricsCollectors.MetricNameStrategy metricNameStrategy) {
        DynamicType.Builder<MetricsCollector> base = new ByteBuddy().subclass(MetricsCollector.class).implement(type);
        DynamicType.Builder<MetricsCollector> builder = withAddedMethods(type, metricNameStrategy, base);
        return loaded(builder);
    }

    private static <T extends MetricsCollector> DynamicType.Builder<T> withAddedMethods(
            Class<?> type,
            MetricsCollectors.MetricNameStrategy metricNameStrategy,
            DynamicType.Builder<T> base) {
        return Stream.of(type.getDeclaredMethods()).reduce(base,
                (builder, method) -> createMethod(builder, method, metricNameStrategy),
                MetricsBuddy::failIfCombined);
    }

    private static <T extends MetricsCollector> DynamicType.Builder<T> createMethod(
            DynamicType.Builder<T> builder,
            Method method,
            MetricsCollectors.MetricNameStrategy metricNameStrategy) {
        MethodCall baseCall = MethodCall.invoke(baseMethod(method)).onSuper();
        String name = metricName(metricNameStrategy, method);
        MethodCall argumentMethodCall = method.getParameterCount() == 0
                ? baseCall.with(name)
                : baseCall.with(name).withArgument(indices(method));
        return builder
                .method(named(method.getName()).and(returns(method.getReturnType())))
                .intercept(argumentMethodCall).modifiers(Visibility.PUBLIC);
    }

    private static String metricName(MetricsCollectors.MetricNameStrategy metricNameStrategy, Method method) {
        OverrideName overrideName = method.getAnnotation(OverrideName.class);
        return overrideName != null ? overrideName.value()
                : metricNameStrategy != null ? metricNameStrategy.metricName(method)
                : method.getName();
    }

    private static int[] indices(Method method) {
        return IntStream.range(0, method.getParameterCount()).toArray();
    }

    private static final Collection<Class<? extends Annotation>> annotations = new HashSet<>(Arrays.asList(
            Inc.class,
            Histo.class,
            Time.class));

    private static Class<? extends MetricsCollector> loaded(DynamicType.Builder<MetricsCollector> baseBuilder) {
        return baseBuilder.make()
                .load(Thread.currentThread().getContextClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();
    }

    private static Method baseMethod(Method method) {
        Class<?> annotation = metricAnnotation(method);
        Optional<Method> first = baseMethods.entrySet().stream()
                .filter(e -> e.getKey().isAssignableFrom(annotation))
                .flatMap(e -> e.getValue().stream())
                .filter(m -> m.getParameterCount() == method.getParameterCount() + 1)
                .findFirst();
        if (!first.isPresent()) {
            throw new IllegalStateException("No corresponding superclass method for " + method +
                    " with " + Arrays.toString(method.getDeclaredAnnotations()));
        }
        return first.get();
    }

    private static Class<?> metricAnnotation(Method method) {
        return Stream.of(method.getAnnotations())
                .filter(MetricsBuddy::isMetricAnnotation)
                .findFirst()
                .map(Object::getClass)
                .orElse(method.getReturnType() == MetricsCollectors.Timer.class
                        ? Time.class
                        : method.getDeclaringClass().getAnnotation(DefaultMetric.class).value());
    }

    private static boolean isMetricAnnotation(Annotation anno) {
        return annotations.stream().anyMatch(annoType -> annoType.isInstance(anno));
    }

    private static Method resolveBaseMethod(String name, Class<?>... args) {
        try {
            return MetricsCollector.class.getDeclaredMethod(name, args);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get " + name + "(" + Arrays.toString(args) + ")", e);
        }
    }

    private static Map<Class<? extends Annotation>, Collection<Method>> baseMethods() {
        Map<Class<? extends Annotation>, Collection<Method>> map = new HashMap<>();
        map.put(Time.class, Collections.singleton(
                resolveBaseMethod("timer", String.class)));
        map.put(Inc.class, Arrays.asList(
                resolveBaseMethod("inc", String.class),
                resolveBaseMethod("inc", String.class, long.class)));
        map.put(Meter.class, Arrays.asList(
                resolveBaseMethod("meter", String.class),
                resolveBaseMethod("meter", String.class, long.class)));
        map.put(Histo.class, Collections.singleton(
                resolveBaseMethod("update", String.class, long.class)));
        return Collections.unmodifiableMap(map);
    }

    private static final Map<Class<? extends Annotation>, Collection<Method>> baseMethods = baseMethods();

    private static <T extends MetricsCollector> DynamicType.Builder<T> failIfCombined(
            DynamicType.Builder<T> b1,
            DynamicType.Builder<T> b2) {
        throw new IllegalStateException("Should not get here: " + b1 + " + " + b2);
    }

    private MetricsBuddy() {
    }
}
