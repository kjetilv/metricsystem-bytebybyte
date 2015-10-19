package no.scienta.tools.metricbuddy;

import com.codahale.metrics.MetricRegistry;

abstract class ManagedBase {

    private MetricRegistry metricRegistry;

    private Class<?> meteredClass;

    void manage(MetricRegistry metricRegistry, Class<?> meteredClass) {
        this.metricRegistry = metricRegistry;
        this.meteredClass = meteredClass;
    }

    protected Class<?> getMeteredClass() {
        return meteredClass;
    }

    protected MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }
}
