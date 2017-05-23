package no.scienta.alchemy.metricbuddy;

import com.codahale.metrics.MetricRegistry;

abstract class ManagedBase {

    private MetricRegistry metricRegistry;

    private Class<?> meteredClass;

    void manage(MetricRegistry metricRegistry, Class<?> meteredClass) {
        this.metricRegistry = metricRegistry;
        this.meteredClass = meteredClass;
    }

    Class<?> getMeteredClass() {
        return meteredClass;
    }

    MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }
}
