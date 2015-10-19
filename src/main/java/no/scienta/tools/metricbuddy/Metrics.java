package no.scienta.tools.metricbuddy;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;

public final class Metrics implements Function<Class<?>, MetricsSupport> {

    private final MetricRegistry metricRegistry;

    private final MetricsBuddy.MetricNameStrategy metricNameStrategy;

    private final boolean registeredInJmx;

    private final Map<Class<?>, MetricsSupport> meters = new ConcurrentHashMap<>();

    private final Map<Class<?>, Class<? extends MetricsSupport>> generatedMeterTypes = new ConcurrentHashMap<>();

    public Metrics() {
        this(null);
    }

    public Metrics(MetricRegistry metricRegistry) {
        this(metricRegistry, null, false);
    }

    private Metrics(
            MetricRegistry metricRegistry,
            MetricsBuddy.MetricNameStrategy metricNameStrategy,
            boolean registeredInJmx) {
        this.metricRegistry = metricRegistry == null ? new MetricRegistry() : metricRegistry;
        this.metricNameStrategy = metricNameStrategy;
        this.registeredInJmx = registeredInJmx;
    }

    @Override
    public MetricsSupport apply(Class<?> sourceType) {
        return getOrCreateInstance(
                Objects.requireNonNull(sourceType, "source type").getClass(),
                null);
    }

    public <T> T create(Object source, Class<T> meterClass) {
        return create(
                Objects.requireNonNull(source, "source").getClass(),
                Objects.requireNonNull(meterClass, "meter class"));
    }

    public <T> T create(Class<?> sourceType, Class<T> meterClass) {
        return getOrCreateInstance(
                Objects.requireNonNull(sourceType, "source type"),
                Objects.requireNonNull(meterClass, "meter class"));
    }

    public Metrics withSnakeCaseNaming() {
        return withNamingStrategy(new SnakeCaseNamer());
    }

    public Metrics withNamingStrategy(MetricsBuddy.MetricNameStrategy strategy) {
        return new Metrics(metricRegistry, strategy, registeredInJmx);
    }

    public Metrics withJmxRegistration() {
        if (registeredInJmx) {
            return this;
        }
        JmxReporter.forRegistry(metricRegistry).registerWith(ManagementFactory.getPlatformMBeanServer()).build().start();
        return new Metrics(metricRegistry, metricNameStrategy, true);
    }

    public interface Timer {

        void done();
    }

    MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    private <T> T getOrCreateInstance(Class<?> sourceType, Class<T> meterType) {
        MetricsSupport metricsSupport = meters.computeIfAbsent(sourceType, type -> {
            if (meterType == null) {
                throw new IllegalArgumentException("No metrics class registered for " + sourceType);
            }
            return newMetrics(sourceType, meterType);
        });
        return validated(sourceType, meterType, metricsSupport);
    }

    private <T> MetricsSupport newMetrics(Class<?> sourceType, Class<T> meterType) {
        Class<? extends MetricsSupport> metricsClass = metricsBaseSubclass(meterType);
        Constructor<?> constructor = callableConstructor(metricsClass);
        Object obj = newInstance(metricsClass, constructor);
        return managed(sourceType, obj);
    }

    private <T> Class<? extends MetricsSupport> metricsBaseSubclass(Class<T> meterType) {
        if (meterType.isInterface()) {
            return generatedMeterTypes.computeIfAbsent(meterType, intf ->
                    MetricsBuddy.generateSubclass(Validation.vetted(intf), metricNameStrategy));
        }
        if (MetricsSupport.class.isAssignableFrom(meterType)) {
            return meterType.asSubclass(MetricsSupport.class);
        }
        throw new IllegalArgumentException
                ("Required interface type or subclass of " + MetricsSupport.class + ", got: " + meterType);
    }

    private Object newInstance(Class<? extends MetricsSupport> metricsClass, Constructor<?> constructor) {
        try {
            return constructor.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to instantiate " + metricsClass, e);
        }
    }

    private Constructor<?> callableConstructor(Class<? extends MetricsSupport> metricsClass) {
        try {
            for (Constructor<?> constructor : metricsClass.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == 0) {
                    constructor.setAccessible(true);
                    return constructor;
                }
            }
        } catch (SecurityException e) {
            throw new IllegalStateException("Failed to find empty constructor of " + metricsClass, e);
        }
        throw new IllegalArgumentException
                ("Failed to instantiate " + metricsClass + ", no empty constructor found: " +
                        Arrays.toString(metricsClass.getDeclaredConstructors()));
    }

    private MetricsSupport managed(Class<?> sourceType, Object obj) {
        MetricsSupport meters = MetricsSupport.class.cast(obj);
        meters.manage(metricRegistry, sourceType);
        return meters;
    }

    private static <T> T validated(Class<?> sourceType, Class<T> meterType, MetricsSupport metricsSupport) {
        if (metricsSupport.getMeteredClass() != sourceType) {
            throw new IllegalArgumentException("Metrics class " + metricsSupport.getClass() +
                    " already registered as metering for " + metricsSupport.getMeteredClass() +
                    ", cannot use it again for " + sourceType);
        }
        if (meterType.isInstance(metricsSupport)) {
            return meterType.cast(metricsSupport);
        }

        throw new IllegalArgumentException("Metrics class for " + sourceType +
                " already registered as " + metricsSupport.getClass() +
                ", could not register new: " + meterType);
    }
}
