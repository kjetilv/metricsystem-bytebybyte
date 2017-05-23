package no.scienta.alchemy.metricbuddy;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The main class.
 */
@SuppressWarnings("WeakerAccess")
public final class MetricsCollectorsImpl implements MetricsCollectors {

    private static MetricsCollectorsImpl INSTANCE = new MetricsCollectorsImpl();

    public static MetricsCollectors defaultInstance() {
        return INSTANCE;
    }

    private final MetricRegistry metricRegistry;

    private final MetricNameStrategy metricNameStrategy;

    private final boolean registeredInJmx;

    private final Map<Class<?>, AbstractMetricsCollector> meters = new ConcurrentHashMap<>();

    private final Map<Class<?>, Class<? extends AbstractMetricsCollector>> generatedMeterTypes = new ConcurrentHashMap<>();

    public MetricsCollectorsImpl() {
        this(null);
    }

    public MetricsCollectorsImpl(MetricRegistry metricRegistry) {
        this(metricRegistry, null, false);
    }

    private MetricsCollectorsImpl(
            MetricRegistry metricRegistry,
            MetricNameStrategy metricNameStrategy,
            boolean registeredInJmx) {
        this.metricRegistry = metricRegistry == null ? new MetricRegistry() : metricRegistry;
        this.metricNameStrategy = metricNameStrategy;
        this.registeredInJmx = registeredInJmx;
    }

    @Override
    public <T> T metricsCollector(Object metricSource, Class<T> metricsCollectorType) {
        return metricsCollector(
                Objects.requireNonNull(metricSource, "source").getClass(),
                Objects.requireNonNull(metricsCollectorType, "meter class"));
    }

    @Override
    public <T> T metricsCollector(Class<?> metricSourceType, Class<T> metricsCollectorType) {
        return getOrCreateInstance(
                Objects.requireNonNull(metricSourceType, "source type"),
                Objects.requireNonNull(metricsCollectorType, "meter class"));
    }

    @Override
    public MetricsCollectors withNameStrategy(MetricNameStrategy strategy) {
        return new MetricsCollectorsImpl(metricRegistry, strategy, registeredInJmx);
    }

    @Override
    public MetricsCollectors withJmxRegistration() {
        if (registeredInJmx) {
            return this;
        }
        JmxReporter.forRegistry(metricRegistry).registerWith(ManagementFactory.getPlatformMBeanServer()).build().start();
        return new MetricsCollectorsImpl(metricRegistry, metricNameStrategy, true);
    }

    MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    private <T> T getOrCreateInstance(Class<?> metricSourceType, Class<T> metricsCollectorType) {
        AbstractMetricsCollector metricsCollector = meters.computeIfAbsent(metricSourceType, type -> {
            if (metricsCollectorType == null) {
                throw new IllegalArgumentException("No metrics class registered for " + metricSourceType);
            }
            return newMetrics(metricSourceType, metricsCollectorType);
        });
        return validated(metricSourceType, metricsCollectorType, metricsCollector);
    }

    private <T> AbstractMetricsCollector newMetrics(Class<?> metricSourceType, Class<T> metricsCollectorType) {
        Class<? extends AbstractMetricsCollector> collectorClass = metricsCollectorClass(metricsCollectorType);
        Constructor<?> constructor = callableConstructor(collectorClass);
        Object instance = newCollectorInstance(constructor);
        return managedMetricsCollector(metricSourceType, instance);
    }

    private <T> Class<? extends AbstractMetricsCollector> metricsCollectorClass(Class<T> type) {
        if (type.isInterface()) {
            return generatedMeterTypes.computeIfAbsent(type, intf ->
                    MetricsBuddy.generateSubclass(Validation.vetted(intf), metricNameStrategy));
        }
        if (AbstractMetricsCollector.class.isAssignableFrom(type)) {
            return type.asSubclass(AbstractMetricsCollector.class);
        }
        throw new IllegalArgumentException
                ("Required interface type or subclass of " + AbstractMetricsCollector.class + ", got: " + type);
    }

    private Object newCollectorInstance(Constructor<?> constructor) {
        try {
            return constructor.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to instantiate " + constructor.getDeclaringClass(), e);
        }
    }

    private Constructor<?> callableConstructor(Class<? extends AbstractMetricsCollector> collectorClass) {
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

    private AbstractMetricsCollector managedMetricsCollector(Class<?> metricSourceType, Object metricsCollector) {
        AbstractMetricsCollector meters = AbstractMetricsCollector.class.cast(metricsCollector);
        meters.manage(metricRegistry, metricSourceType);
        return meters;
    }

    private static <T> T validated(Class<?> sourceType, Class<T> meterType, AbstractMetricsCollector metricsCollector) {
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
