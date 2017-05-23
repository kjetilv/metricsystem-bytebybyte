package no.scienta.alchemy.metricbuddy;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MetricsCollector {

    Class<? extends Annotation> defaultMetric() default Meter.class;
}
