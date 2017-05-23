package no.scienta.alchemy.metricbuddy;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DefaultMetric {

    Class<? extends Annotation> value();
}
