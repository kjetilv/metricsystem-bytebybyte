package no.scienta.tools.metricbuddy;

import java.lang.reflect.Method;

/**
 * The main interface. Produces metrics collectors from subclasses of {@link MetricsCollector}, or from annotated
 * interface types.
 */
public interface MetricsCollectors {

    /**
     * Get the metrics collector for the source.
     *
     * @param <T> Collector interface type
     * @param metricSource An instance of the source type
     * @param metricsCollectorType Collector interface
     * @return Metrics collector
     */
    <T> T metricsCollector(Object metricSource, Class<T> metricsCollectorType);

    /**
     * Get the metrics collector for the source.
     *
     * @param metricSourceType The source type
     * @param metricsCollectorType Collector interface
     * @param <T> Collector interface type
     * @return Metrics collector
     */
    <T> T metricsCollector(Class<?> metricSourceType, Class<T> metricsCollectorType);

    /**
     * Register metrics collectors in JMX.
     *
     * @return Metrics collectors with HMX registration
     */
    MetricsCollectors withJmxRegistration();

    /**
     * @return Metrics collectors with snake_case_naming.
     */
    default MetricsCollectors withSnakeCaseNaming() {
        return withNamingStrategy(new SnakeCaseNamer());
    };

    /**
     * @return Metrics collectors with path.naming.
     */
    default MetricsCollectors withPathNaming() {
        return withNamingStrategy(new PathNamer());
    };

    /**
     * @param strategy Naming strategy
     * @return Metrics collectors with the given naming.
     */
    MetricsCollectors withNamingStrategy(MetricNameStrategy strategy);

    /**
     * A timer interface.
     */
    interface Timer {

        void done();
    }

    /**
     * A naming strategy for metric, from a method
     */
    interface MetricNameStrategy {

        String metricName(Method metricMethod);
    }
}
