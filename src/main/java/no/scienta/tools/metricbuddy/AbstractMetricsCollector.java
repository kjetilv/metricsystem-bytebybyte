package no.scienta.tools.metricbuddy;

import com.codahale.metrics.MetricRegistry;

import java.util.concurrent.Callable;

/**
 * Superclass for metrics collectors.  A {@link MetricsCollectors} instance creates instances of this class, either
 * from subclasses or annotated interfaces.
 */
public abstract class AbstractMetricsCollector extends ManagedBase {

    protected <T> T time(String name, Callable<T> callable) {
        MetricsCollectors.Timer timer = timer(name);
        try {
            return callable.call();
        } catch (Exception e) {
            throw new IllegalArgumentException("Timing of '" + name + "' " + callable + " failed", e);
        } finally {
            timer.done();
        }
    }

    /**
     * Get the named timer.
     *
     * @param name Timer name
     * @return Timer
     */
    protected MetricsCollectors.Timer timer(String name) {
        return getMetricRegistry().timer(MetricRegistry.name(getMeteredClass(), name)).time()::stop;
    }

    /**
     * Update the named histogram.
     *
     * @param name Histogram name
     * @param i Value
     */
    protected void update(String name, long i) {
        getMetricRegistry().histogram(MetricRegistry.name(getMeteredClass(), name)).update(i);
    }

    /**
     * Increment the named counter.
     *
     * @param name Counter name
     */
    protected void inc(String name) {
        inc(name, 1L);
    }

    /**
     * Increment the named counter.
     *
     * @param name Counter name
     * @param i Increment
     */
    protected void inc(String name, long i) {
        getMetricRegistry().counter(MetricRegistry.name(getMeteredClass(), name)).inc(i);
    }

    /**
     * Decrement the named counter.
     *
     * @param name Counter name
     */
    protected void dec(String name) {
        dec(name, 1L);
    }

    /**
     * Decrement the named counter.
     *
     * @param name Counter name
     * @param i Decrement
     */
    protected void dec(String name, long i) {
        getMetricRegistry().counter(MetricRegistry.name(getMeteredClass(), name)).dec(i);
    }

    /**
     * Increment the named meter.
     * @param name Meter name
     */
    protected void meter(String name) {
        meter(name, 1L);
    }

    /**
     * Increment the named meter.
     *
     * @param name Meter name
     * @param i Increment
     */
    protected void meter(String name, long i) {
        getMetricRegistry().meter(MetricRegistry.name(getMeteredClass(), name)).mark(i);
    }
}
