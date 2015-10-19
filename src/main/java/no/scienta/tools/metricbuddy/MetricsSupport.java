package no.scienta.tools.metricbuddy;

import com.codahale.metrics.MetricRegistry;

import java.util.concurrent.Callable;

@SuppressWarnings({"InstanceVariableMayNotBeInitialized", "unused"})
public abstract class MetricsSupport extends ManagedBase {

    protected <T> T time(String name, Callable<T> callable) {
        Metrics.Timer timer = timer(name);
        try {
            return callable.call();
        } catch (Exception e) {
            throw new IllegalArgumentException("Timing of '" + name + "' " + callable + " failed", e);
        } finally {
            timer.done();
        }
    }

    protected Metrics.Timer timer(String name) {
        return getMetricRegistry().timer(MetricRegistry.name(getMeteredClass(), name)).time()::stop;
    }

    protected void update(String name, long i) {
        getMetricRegistry().histogram(MetricRegistry.name(getMeteredClass(), name)).update(i);
    }

    protected void inc(String name) {
        inc(name, 1L);
    }

    protected void inc(String name, long i) {
        getMetricRegistry().counter(MetricRegistry.name(getMeteredClass(), name)).inc(i);
    }

    protected void meter(String name) {
        meter(name, 1L);
    }

    protected void meter(String name, long i) {
        getMetricRegistry().meter(MetricRegistry.name(getMeteredClass(), name)).mark(i);
    }

    protected void dec(String name) {
        dec(name, 1L);
    }

    protected void dec(String name, long i) {
        getMetricRegistry().counter(MetricRegistry.name(getMeteredClass(), name)).dec(i);
    }
}
