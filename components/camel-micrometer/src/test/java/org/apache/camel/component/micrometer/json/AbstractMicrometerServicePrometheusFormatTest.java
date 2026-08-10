/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.micrometer.json;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.LongTaskTimer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AbstractMicrometerServicePrometheusFormatTest {

    // ── field default ────────────────────────────────────────────────────────

    @Test
    void defaultFormatIsJson() {
        // verify default without instantiating a full service context
        // by exercising the getter through a concrete subclass stub
        AbstractMicrometerService svc = new AbstractMicrometerService() {
        };
        assertEquals("json", svc.getLogMetricsOnShutdownFormat());
    }

    @Test
    void setFormatPrometheusRoundTrips() {
        AbstractMicrometerService svc = new AbstractMicrometerService() {
        };
        svc.setLogMetricsOnShutdownFormat("prometheus");
        assertEquals("prometheus", svc.getLogMetricsOnShutdownFormat());
    }

    // ── Gauge ────────────────────────────────────────────────────────────────

    @Test
    void gaugeProducesHelpTypeAndValue() {
        Gauge gauge = mock(Gauge.class);
        Meter.Id id = new Meter.Id("app.info", Tags.empty(), null, "Application info", Meter.Type.GAUGE);
        when(gauge.getId()).thenReturn(id);
        when(gauge.value()).thenReturn(1.0);

        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(gauge);

        assertEquals("# HELP app_info Application info", lines.get(0));
        assertEquals("# TYPE app_info gauge", lines.get(1));
        assertEquals("app_info 1.0", lines.get(2));
        assertEquals(3, lines.size());
    }

    @Test
    void gaugeNaNIsRenderedAsNaN() {
        Gauge gauge = mock(Gauge.class);
        Meter.Id id = new Meter.Id("my.gauge", Tags.empty(), null, null, Meter.Type.GAUGE);
        when(gauge.getId()).thenReturn(id);
        when(gauge.value()).thenReturn(Double.NaN);

        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(gauge);

        assertTrue(lines.get(2).endsWith(" NaN"));
    }

    // ── Counter ──────────────────────────────────────────────────────────────

    @Test
    void counterProducesTotalSuffix() {
        Counter counter = mock(Counter.class);
        Meter.Id id = new Meter.Id("camel.exchanges.completed", Tags.empty(), null, null, Meter.Type.COUNTER);
        when(counter.getId()).thenReturn(id);
        when(counter.count()).thenReturn(42.0);

        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(counter);

        assertEquals("# HELP camel_exchanges_completed_total camel_exchanges_completed", lines.get(0));
        assertEquals("# TYPE camel_exchanges_completed_total counter", lines.get(1));
        assertEquals("camel_exchanges_completed_total 42.0", lines.get(2));
        assertEquals(3, lines.size());
    }

    // ── Timer ────────────────────────────────────────────────────────────────

    @Test
    void timerProducesCountSumMaxInSeconds() {
        Timer timer = mock(Timer.class);
        Meter.Id id = new Meter.Id("camel.route.duration", Tags.empty(), null, "Route duration", Meter.Type.TIMER);
        when(timer.getId()).thenReturn(id);
        when(timer.count()).thenReturn(10L);
        when(timer.totalTime(TimeUnit.SECONDS)).thenReturn(2.5);
        when(timer.max(TimeUnit.SECONDS)).thenReturn(0.8);

        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(timer);

        assertEquals("# HELP camel_route_duration_seconds Route duration", lines.get(0));
        assertEquals("# TYPE camel_route_duration_seconds summary", lines.get(1));
        assertEquals("camel_route_duration_seconds_count 10", lines.get(2));
        assertEquals("camel_route_duration_seconds_sum 2.5", lines.get(3));
        assertEquals("camel_route_duration_seconds_max 0.8", lines.get(4));
        assertEquals(5, lines.size());
    }

    // ── DistributionSummary ───────────────────────────────────────────────────

    @Test
    void distributionSummaryProducesCountSumMax() {
        DistributionSummary ds = mock(DistributionSummary.class);
        Meter.Id id = new Meter.Id("payload.size", Tags.empty(), null, null, Meter.Type.DISTRIBUTION_SUMMARY);
        when(ds.getId()).thenReturn(id);
        when(ds.count()).thenReturn(5L);
        when(ds.totalAmount()).thenReturn(500.0);
        when(ds.max()).thenReturn(200.0);

        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(ds);

        assertEquals("# TYPE payload_size summary", lines.get(1));
        assertEquals("payload_size_count 5", lines.get(2));
        assertEquals("payload_size_sum 500.0", lines.get(3));
        assertEquals("payload_size_max 200.0", lines.get(4));
    }

    // ── FunctionCounter ───────────────────────────────────────────────────────

    @Test
    void functionCounterProducesTotalSuffix() {
        FunctionCounter fc = mock(FunctionCounter.class);
        Meter.Id id = new Meter.Id("my.fc", Tags.empty(), null, null, Meter.Type.COUNTER);
        when(fc.getId()).thenReturn(id);
        when(fc.count()).thenReturn(7.0);

        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(fc);

        assertTrue(lines.stream().anyMatch(l -> l.equals("# TYPE my_fc_total counter")));
        assertTrue(lines.stream().anyMatch(l -> l.equals("my_fc_total 7.0")));
    }

    // ── LongTaskTimer ─────────────────────────────────────────────────────────

    @Test
    void longTaskTimerProducesActiveTasksDurationMax() {
        LongTaskTimer ltt = mock(LongTaskTimer.class);
        Meter.Id id = new Meter.Id("camel.long.task", Tags.empty(), null, "Long running task", Meter.Type.LONG_TASK_TIMER);
        when(ltt.getId()).thenReturn(id);
        when(ltt.activeTasks()).thenReturn(3);
        when(ltt.duration(TimeUnit.SECONDS)).thenReturn(12.5);
        when(ltt.max(TimeUnit.SECONDS)).thenReturn(6.0);

        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(ltt);

        assertEquals("# HELP camel_long_task_active_seconds Long running task", lines.get(0));
        assertEquals("# TYPE camel_long_task_active_seconds gauge", lines.get(1));
        assertEquals("camel_long_task_active_seconds_active 3", lines.get(2));
        assertEquals("camel_long_task_active_seconds_duration 12.5", lines.get(3));
        assertEquals("camel_long_task_active_seconds_max 6.0", lines.get(4));
        assertEquals(5, lines.size());
    }

    // ── FunctionTimer ─────────────────────────────────────────────────────────

    @Test
    void functionTimerProducesCountAndSum() {
        FunctionTimer ft = mock(FunctionTimer.class);
        Meter.Id id = new Meter.Id("my.ft", Tags.empty(), null, null, Meter.Type.TIMER);
        when(ft.getId()).thenReturn(id);
        when(ft.count()).thenReturn(3.0);
        when(ft.totalTime(TimeUnit.SECONDS)).thenReturn(1.5);

        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(ft);

        assertTrue(lines.stream().anyMatch(l -> l.equals("my_ft_seconds_count 3.0")));
        assertTrue(lines.stream().anyMatch(l -> l.equals("my_ft_seconds_sum 1.5")));
    }

    // ── label encoding ────────────────────────────────────────────────────────

    @Test
    void tagsAreRenderedAsPrometheusLabels() {
        Gauge gauge = mock(Gauge.class);
        Meter.Id id = new Meter.Id(
                "app.info",
                Tags.of("camel.version", "4.0.0", "camel.context", "my-ctx"),
                null, null, Meter.Type.GAUGE);
        when(gauge.getId()).thenReturn(id);
        when(gauge.value()).thenReturn(0.0);

        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(gauge);

        String valueLine = lines.get(2);
        assertTrue(valueLine.startsWith("app_info{"), "value line should carry labels");
        assertTrue(valueLine.contains("camel_version=\"4.0.0\""));
        assertTrue(valueLine.contains("camel_context=\"my-ctx\""));
    }

    @Test
    void specialCharsInTagValuesAreEscaped() {
        Gauge gauge = mock(Gauge.class);
        Meter.Id id = new Meter.Id(
                "my.gauge",
                Tags.of("path", "say\\\"hello\"\nworld"),
                null, null, Meter.Type.GAUGE);
        when(gauge.getId()).thenReturn(id);
        when(gauge.value()).thenReturn(1.0);

        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(gauge);
        String valueLine = lines.get(2);

        assertTrue(valueLine.contains("\\\\"), "backslash must be escaped");
        assertTrue(valueLine.contains("\\\""), "quote must be escaped");
        assertTrue(valueLine.contains("\\n"), "newline must be escaped");
    }
}
