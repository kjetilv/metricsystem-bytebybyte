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

/**
 * The main class.  Maps from a
 */
public final class MetricsCollectors implements Function<Class<?>, MetricsCollector> {

    private final MetricRegistry metricRegistry;

    private final MetricsBuddy.MetricNameStrategy metricNameStrategy;

    private final boolean registeredInJmx;

    private final Map<Class<?>, MetricsCollector> meters = new ConcurrentHashMap<>();

    private final Map<Class<?>, Class<? extends MetricsCollector>> generatedMeterTypes = new ConcurrentHashMap<>();

    public MetricsCollectors() {
        this(null);
    }

    public MetricsCollectors(MetricRegistry metricRegistry) {
        this(metricRegistry, null, false);
    }

    private MetricsCollectors(
            MetricRegistry metricRegistry,
            MetricsBuddy.MetricNameStrategy metricNameStrategy,
            boolean registeredInJmx) {
        this.metricRegistry = metricRegistry == null ? new MetricRegistry() : metricRegistry;
        this.metricNameStrategy = metricNameStrategy;
        this.registeredInJmx = registeredInJmx;
    }

    /**
     * @param sourceType The source of metrics
     * @return The metrics collector
     */
    @Override
    public MetricsCollector apply(Class<?> sourceType) {
        return getOrCreateInstance(
                Objects.requireNonNull(sourceType, "source type").getClass(),
                null);
    }

    public <T> T metricsCollector(Object source, Class<T> metricsCollectorType) {
        return metricsCollector(
                Objects.requireNonNull(source, "source").getClass(),
                Objects.requireNonNull(metricsCollectorType, "meter class"));
    }

    public <T> T metricsCollector(Class<?> metricSourceType, Class<T> metricsCollectorType) {
        return getOrCreateInstance(
                Objects.requireNonNull(metricSourceType, "source type"),
                Objects.requireNonNull(metricsCollectorType, "meter class"));
    }

    public MetricsCollectors withSnakeCaseNaming() {
        return withNamingStrategy(new SnakeCaseNamer());
    }

    public MetricsCollectors withPathNaming() {
        return withNamingStrategy(new PathNamer());
    }

    public MetricsCollectors withNamingStrategy(MetricsBuddy.MetricNameStrategy strategy) {
        return new MetricsCollectors(metricRegistry, strategy, registeredInJmx);
    }

    public MetricsCollectors withJmxRegistration() {
        if (registeredInJmx) {
            return this;
        }
        JmxReporter.forRegistry(metricRegistry).registerWith(ManagementFactory.getPlatformMBeanServer()).build().start();
        return new MetricsCollectors(metricRegistry, metricNameStrategy, true);
    }

    public interface Timer {

        void done();
    }

    MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    private <T> T getOrCreateInstance(Class<?> metricSourceType, Class<T> metricsCollectorType) {
        MetricsCollector metricsCollector = meters.computeIfAbsent(metricSourceType, type -> {
            if (metricsCollectorType == null) {
                throw new IllegalArgumentException("No metrics class registered for " + metricSourceType);
            }
            return newMetrics(metricSourceType, metricsCollectorType);
        });
        return validated(metricSourceType, metricsCollectorType, metricsCollector);
    }

    private <T> MetricsCollector newMetrics(Class<?> metricSourceType, Class<T> metricsCollectorType) {
        Class<? extends MetricsCollector> collectorClass = metricsCollectorClass(metricsCollectorType);
        Constructor<?> constructor = callableConstructor(collectorClass);
        Object instance = newCollectorInstance(constructor);
        return managedMetricsCollector(metricSourceType, instance);
    }

    private <T> Class<? extends MetricsCollector> metricsCollectorClass(Class<T> type) {
        if (type.isInterface()) {
            return generatedMeterTypes.computeIfAbsent(type, intf ->
                    MetricsBuddy.generateSubclass(Validation.vetted(intf), metricNameStrategy));
        }
        if (MetricsCollector.class.isAssignableFrom(type)) {
            return type.asSubclass(MetricsCollector.class);
        }
        throw new IllegalArgumentException
                ("Required interface type or subclass of " + MetricsCollector.class + ", got: " + type);
    }

    private Object newCollectorInstance(Constructor<?> constructor) {
        try {
            return constructor.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to instantiate " + constructor.getDeclaringClass(), e);
        }
    }

    private Constructor<?> callableConstructor(Class<? extends MetricsCollector> collectorClass) {
        try {
            for (Constructor<?> constructor : collectorClass.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == 0) {
                    constructor.setAccessible(true);
                    return constructor;
                }
            }
        } catch (SecurityException e) {
            throw new IllegalStateException("Failed to find empty constructor of " + collectorClass, e);
        }
        throw new IllegalArgumentException
                ("Failed to instantiate " + collectorClass + ", no empty constructor found: " +
                        Arrays.toString(collectorClass.getDeclaredConstructors()));
    }

    private MetricsCollector managedMetricsCollector(Class<?> metricSourceType, Object metricsCollector) {
        MetricsCollector meters = MetricsCollector.class.cast(metricsCollector);
        meters.manage(metricRegistry, metricSourceType);
        return meters;
    }

    private static <T> T validated(Class<?> sourceType, Class<T> meterType, MetricsCollector metricsCollector) {
        if (metricsCollector.getMeteredClass() != sourceType) {
            throw new IllegalArgumentException("Metrics class " + metricsCollector.getClass() +
                    " already registered as metering for " + metricsCollector.getMeteredClass() +
                    ", cannot use it again for " + sourceType);
        }
        if (meterType.isInstance(metricsCollector)) {
            return meterType.cast(metricsCollector);
        }

        throw new IllegalArgumentException("Metrics class for " + sourceType +
                " already registered as " + metricsCollector.getClass() +
                ", could not register new: " + meterType);
    }
}
