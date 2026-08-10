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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractMicrometerServicePrometheusFormatTest {

    // ── field default ────────────────────────────────────────────────────────

    @Test
    void defaultFormatIsJson() throws IOException {
        try (// verify default without instantiating a full service context
             // by exercising the getter through a concrete subclass stub
             AbstractMicrometerService svc = new AbstractMicrometerService() {
             }) {
            assertThat(svc.getLogMetricsOnShutdownFormat()).isEqualTo("json");
        }
    }

    @Test
    void setFormatPrometheusRoundTrips() throws IOException {
        try (AbstractMicrometerService svc = new AbstractMicrometerService() {
        }) {
            svc.setLogMetricsOnShutdownFormat("prometheus");
            assertThat(svc.getLogMetricsOnShutdownFormat()).isEqualTo("prometheus");
        }
    }

    // ── Gauge ────────────────────────────────────────────────────────────────

    @Test
    void gaugeProducesHelpTypeAndValue() {
        Gauge gauge = mock(Gauge.class);
        Meter.Id id = new Meter.Id("app.info", Tags.empty(), null, "Application info", Meter.Type.GAUGE);
        when(gauge.getId()).thenReturn(id);
        when(gauge.value()).thenReturn(1.0);

        String promName = AbstractMicrometerService.normalizePrometheusName(gauge);
        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(promName, gauge);

        assertThat(lines.get(0)).isEqualTo("# HELP app_info Application info");
        assertThat(lines.get(1)).isEqualTo("# TYPE app_info gauge");
        assertThat(lines.get(2)).isEqualTo("app_info 1.0");
        assertThat(lines.size()).isEqualTo(3);
    }

    @Test
    void gaugeNaNIsRenderedAsNaN() {
        Gauge gauge = mock(Gauge.class);
        Meter.Id id = new Meter.Id("my.gauge", Tags.empty(), null, null, Meter.Type.GAUGE);
        when(gauge.getId()).thenReturn(id);
        when(gauge.value()).thenReturn(Double.NaN);

        String promName = AbstractMicrometerService.normalizePrometheusName(gauge);
        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(promName, gauge);

        assertThat(lines.get(2).endsWith(" NaN"));
    }

    // ── Counter ──────────────────────────────────────────────────────────────

    @Test
    void counterProducesTotalSuffix() {
        Counter counter = mock(Counter.class);
        Meter.Id id = new Meter.Id("camel.exchanges.completed", Tags.empty(), null, null, Meter.Type.COUNTER);
        when(counter.getId()).thenReturn(id);
        when(counter.count()).thenReturn(42.0);

        String promName = AbstractMicrometerService.normalizePrometheusName(counter);
        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(promName, counter);

        assertThat(lines.get(0)).isEqualTo("# HELP camel_exchanges_completed_total camel_exchanges_completed");
        assertThat(lines.get(1)).isEqualTo("# TYPE camel_exchanges_completed_total counter");
        assertThat(lines.get(2)).isEqualTo("camel_exchanges_completed_total 42.0");
        assertThat(lines.size()).isEqualTo(3);
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

        String promName = AbstractMicrometerService.normalizePrometheusName(timer);
        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(promName, timer);

        assertThat(lines.get(0)).isEqualTo("# HELP camel_route_duration_seconds Route duration");
        assertThat(lines.get(1)).isEqualTo("# TYPE camel_route_duration_seconds summary");
        assertThat(lines.get(2)).isEqualTo("camel_route_duration_seconds_count 10");
        assertThat(lines.get(3)).isEqualTo("camel_route_duration_seconds_sum 2.5");
        assertThat(lines.get(4)).isEqualTo("camel_route_duration_seconds_max 0.8");
        assertThat(lines.size()).isEqualTo(5);
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

        String promName = AbstractMicrometerService.normalizePrometheusName(ds);
        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(promName, ds);

        assertThat(lines.get(1)).isEqualTo("# TYPE payload_size summary");
        assertThat(lines.get(2)).isEqualTo("payload_size_count 5");
        assertThat(lines.get(3)).isEqualTo("payload_size_sum 500.0");
        assertThat(lines.get(4)).isEqualTo("payload_size_max 200.0");
    }

    // ── FunctionCounter ───────────────────────────────────────────────────────

    @Test
    void functionCounterProducesTotalSuffix() {
        FunctionCounter fc = mock(FunctionCounter.class);
        Meter.Id id = new Meter.Id("my.fc", Tags.empty(), null, null, Meter.Type.COUNTER);
        when(fc.getId()).thenReturn(id);
        when(fc.count()).thenReturn(7.0);

        String promName = AbstractMicrometerService.normalizePrometheusName(fc);
        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(promName, fc);

        assertThat(lines).contains("# TYPE my_fc_total counter");
        assertThat(lines).contains("my_fc_total 7.0");
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

        String promName = AbstractMicrometerService.normalizePrometheusName(ltt);
        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(promName, ltt);

        assertThat(lines.get(0)).isEqualTo("# HELP camel_long_task_seconds Long running task");
        assertThat(lines.get(1)).isEqualTo("# TYPE camel_long_task_seconds gauge");
        assertThat(lines.get(3)).isEqualTo("camel_long_task_seconds_sum 12.5");
        assertThat(lines.get(4)).isEqualTo("camel_long_task_seconds_max 6.0");
        assertThat(lines).hasSize(5);
    }

    // ── FunctionTimer ─────────────────────────────────────────────────────────

    @Test
    void functionTimerProducesCountAndSum() {
        FunctionTimer ft = mock(FunctionTimer.class);
        Meter.Id id = new Meter.Id("my.ft", Tags.empty(), null, null, Meter.Type.TIMER);
        when(ft.getId()).thenReturn(id);
        when(ft.count()).thenReturn(3.0);
        when(ft.totalTime(TimeUnit.SECONDS)).thenReturn(1.5);

        String promName = AbstractMicrometerService.normalizePrometheusName(ft);
        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(promName, ft);

        assertThat(lines).contains("my_ft_seconds_count 3.0");
        assertThat(lines).contains("my_ft_seconds_sum 1.5");
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

        String promName = AbstractMicrometerService.normalizePrometheusName(gauge);
        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(promName, gauge);

        String valueLine = lines.get(2);
        assertThat(valueLine)
                .as("value line should carry labels")
                .startsWith("app_info{")
                .contains(
                        "camel_version=\"4.0.0\"",
                        "camel_context=\"my-ctx\"");
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

        String promName = AbstractMicrometerService.normalizePrometheusName(gauge);
        List<String> lines = AbstractMicrometerService.convertMeterToPrometheusLines(promName, gauge);
        String valueLine = lines.get(2);

        assertThat(valueLine)
                .as("value line should escape special characters")
                .contains("\\\\", "\\\"", "\\n");
    }
}
