package no.scienta.tools.metricbuddy.test;

import java.util.SortedMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

import com.codahale.metrics.*;
import no.scienta.tools.metricbuddy.*;
import no.scienta.tools.metricbuddy.Meter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MetricsCollectorsTest {

    private MetricRegistry registry;

    private MetricsCollectors metricsCollectors;

    @Before
    public void setup() {
        registry = new MetricRegistry();
        metricsCollectors = new MetricsCollectorsImpl(registry).withSnakeCaseNaming();
    }

    @After
    public void teardown() {
        registry = null;
        metricsCollectors = null;
    }

    /**
     * A sample interface that can be used to trigger metrics.
     */
    @DefaultMetric(Inc.class)
    public interface MetricsTestMetrics {

        void testRun();

        @Inc
        void testFailed(); // redundant

        void testSteps(long steps);

        @OverrideName("baloney") // renamed incrementer
        void testBogus();

        @Meter
        void testMeter();

        @Meter
        void testMetering(long meter);

        @Histo // not a counter
        void testLength(long milliseconds);

        @Histo @OverrideName("bigness")
        void testSize(long milliseconds);

        MetricsCollectors.Timer testTimer();
    }

    @Test
    public void testIdentity() {
        assertSame("Metrics instance should be the same for the same class", mtm(), mtm());
        MetricsTestMetrics byClass = metricsCollectors.metricsCollector(MetricsCollectorsTest.class, MetricsTestMetrics.class);
        assertSame("Metrics instance should be the same for the same class", byClass, mtm());
    }

    @Test
    public void testSimpleCounter() {
        mtm().testRun();
        assertCounterValue("test-run", 1L);

        mtm().testRun();
        assertCounterValue("test-run", 2L);

        mtm().testFailed();
        assertCounterValue("test-failed", 1L);
        mtm().testFailed();
        mtm().testFailed();
        assertCounterValue("test-failed", 3L);
    }

    @Test
    public void testNamedCounter() {
        mtm().testBogus();
        assertCounterValue("baloney", 1L);
    }

    @Test
    public void testStepCounter() {
        mtm().testSteps(10L);
        assertCounterValue("test-steps", 10L);
    }

    @Test
    public void testHistogram() {
        mtm().testLength(10L);
        assertHistogramValue("test-length", 1L, 10.0D);

        mtm().testLength(2L);
        assertHistogramValue("test-length", 2L, 6.0D);
    }

    @Test
    public void testNamedHistogram() {
        mtm().testSize(30);
        mtm().testSize(50);
        assertHistogramValue("bigness", 2L, 40.0D);
    }

    @Test
    public void testTimer() throws InterruptedException {
        MetricsCollectors.Timer timer = mtm().testTimer();
        Thread.sleep(100);
        timer.done();

        assertTimerValue("test-timer", 1, 99.0);
    }

    @Test
    public void testMeter() {
        mtm().testMeter();

        assertMeterValue("test-meter", 1, 1.0);
    }

    @Test
    public void testMetering() {
        mtm().testMetering(3);

        assertMeterValue("test-metering", 3, 3.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void badTimerReturnType() {
        fail(metricsCollectors.metricsCollector(this, BadMetrics1.class) + " should not exist!");
    }

    @SuppressWarnings("unused")
    interface BadMetrics1 {
        @Time
        void timer();
    }

    @Test(expected = IllegalArgumentException.class)
    public void badTimerArgumentList() {
        fail(metricsCollectors.metricsCollector(this, BadMetrics2.class) + " should not exist!");
    }

    @SuppressWarnings("unused")
    interface BadMetrics2 {
        @Time
        Timer timer(int bogus);
    }

    @Test(expected = IllegalArgumentException.class)
    public void badCounterReturnType() {
        fail(metricsCollectors.metricsCollector(this, BadMetrics3.class) + " should not exist!");
    }

    @SuppressWarnings("unused")
    interface BadMetrics3 {
        @Inc
        String count();
    }

    @Test(expected = IllegalArgumentException.class)
    public void badHistoReturnType() {
        fail(metricsCollectors.metricsCollector(this, BadMetrics4.class) + " should not exist!");
    }

    @SuppressWarnings("unused")
    interface BadMetrics4 {
        @Histo
        String count();
    }

    private MetricsTestMetrics mtm() {
        return metricsCollectors.metricsCollector(this, MetricsTestMetrics.class);
    }

    private void assertHistogramValue(String name, long count, double mean) {
        SortedMap<String, Histogram> histograms = registry.getHistograms();
        Histogram h = get(histograms, name);
        assertThat(h, notNullValue());
        assertThat(h.getCount(), is(count));
        assertEquals(h.getSnapshot().getMean(), mean, 0.01D);
    }

    private void assertCounterValue(String counter, long value) {
        SortedMap<String, Counter> counters = registry.getCounters();
        Counter c = get(counters, counter);
        assertThat(c, notNullValue());
        assertThat(c.getCount(), is(value));
    }

    private void assertTimerValue(String counter, long value, double mean) {
        SortedMap<String, Timer> timers = registry.getTimers();
        Timer t = get(timers, counter);
        assertThat(t, notNullValue());
        assertThat(t.getCount(), is(value));
        assertTrue(t.getSnapshot().getMean() >= mean);
    }

    private void assertMeterValue(String counter, long value, double mean) {
        SortedMap<String, com.codahale.metrics.Meter> meters = registry.getMeters();
        com.codahale.metrics.Meter t = get(meters, counter);
        assertThat(t, notNullValue());
        assertThat(t.getCount(), is(value));
        assertTrue(t.getMeanRate() >= mean);
    }

    private static <T> T get(SortedMap<String, T> counters, String counter) {
        return counters.get(MetricRegistry.name(MetricsCollectorsTest.class, counter));
    }
}
