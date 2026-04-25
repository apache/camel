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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConvertMeterToMapTest {

    @Test
    void shouldConvertCounter() {
        Counter counter = mock(Counter.class);
        Meter.Id id = new Meter.Id("my.counter", Tags.empty(), null, null, Meter.Type.COUNTER);
        when(counter.getId()).thenReturn(id);
        when(counter.count()).thenReturn(5.0);

        Map<String, Object> result = AbstractMicrometerService.convertMeterToMap(counter);

        assertEquals("my.counter", result.get("name"));
        assertEquals("counter", result.get("type"));
        assertEquals(5.0, result.get("value"));
    }

    @Test
    void shouldConvertGauge() {
        Gauge gauge = mock(Gauge.class);
        Meter.Id id = new Meter.Id("my.gauge", Tags.empty(), null, null, Meter.Type.GAUGE);
        when(gauge.getId()).thenReturn(id);
        when(gauge.value()).thenReturn(42.0);

        Map<String, Object> result = AbstractMicrometerService.convertMeterToMap(gauge);

        assertEquals("my.gauge", result.get("name"));
        assertEquals("gauge", result.get("type"));
        assertEquals(42.0, result.get("value"));
    }

    @Test
    void shouldConvertTimer() {
        Timer timer = mock(Timer.class);
        Meter.Id id = new Meter.Id("my.timer", Tags.empty(), null, null, Meter.Type.TIMER);
        when(timer.getId()).thenReturn(id);
        when(timer.totalTime(TimeUnit.MILLISECONDS)).thenReturn(1000.0);
        when(timer.count()).thenReturn(10L);
        when(timer.max(TimeUnit.MILLISECONDS)).thenReturn(300.0);

        Map<String, Object> result = AbstractMicrometerService.convertMeterToMap(timer);

        assertEquals("my.timer", result.get("name"));
        assertEquals("timer", result.get("type"));
        assertEquals(1000.0, result.get("totalTimeMs"));
        assertEquals(10L, result.get("count"));
        assertEquals(300.0, result.get("maxTimeMs"));
    }

    @Test
    void shouldConvertDistributionSummary() {
        DistributionSummary ds = mock(DistributionSummary.class);
        Meter.Id id = new Meter.Id("my.summary", Tags.empty(), null, null, Meter.Type.DISTRIBUTION_SUMMARY);
        when(ds.getId()).thenReturn(id);
        when(ds.totalAmount()).thenReturn(500.0);
        when(ds.count()).thenReturn(5L);
        when(ds.max()).thenReturn(200.0);

        Map<String, Object> result = AbstractMicrometerService.convertMeterToMap(ds);

        assertEquals("my.summary", result.get("name"));
        assertEquals("summary", result.get("type"));
        assertEquals(500.0, result.get("total"));
        assertEquals(5L, result.get("count"));
        assertEquals(200.0, result.get("max"));
    }

    @Test
    void shouldConvertFunctionCounter() {
        FunctionCounter fc = mock(FunctionCounter.class);
        Meter.Id id = new Meter.Id("my.fc", Tags.empty(), null, null, Meter.Type.COUNTER);
        when(fc.getId()).thenReturn(id);
        when(fc.count()).thenReturn(3.0);

        Map<String, Object> result = AbstractMicrometerService.convertMeterToMap(fc);

        assertEquals("my.fc", result.get("name"));
        assertEquals("functionCounter", result.get("type"));
        assertEquals(3.0, result.get("value"));
    }

    @Test
    void shouldConvertFunctionTimer() {
        FunctionTimer ft = mock(FunctionTimer.class);
        Meter.Id id = new Meter.Id("my.ft", Tags.empty(), null, null, Meter.Type.TIMER);
        when(ft.getId()).thenReturn(id);
        when(ft.count()).thenReturn(4.0);
        when(ft.totalTime(TimeUnit.MILLISECONDS)).thenReturn(1000.0);
        when(ft.mean(TimeUnit.MILLISECONDS)).thenReturn(250.0);

        Map<String, Object> result = AbstractMicrometerService.convertMeterToMap(ft);

        assertEquals("my.ft", result.get("name"));
        assertEquals("functionTimer", result.get("type"));
        assertEquals(4.0, result.get("count"));
        assertEquals(1000.0, result.get("totalTimeMs"));
        assertEquals(250.0, result.get("meanMs"));
    }

    @Test
    void shouldFallbackForUnknownMeterType() {
        Meter meter = mock(Meter.class);
        Meter.Id id = new Meter.Id("unknown.meter", Tags.empty(), null, null, Meter.Type.OTHER);
        when(meter.getId()).thenReturn(id);

        Map<String, Object> result = AbstractMicrometerService.convertMeterToMap(meter);

        assertEquals("unknown.meter", result.get("name"));
        assertEquals("OTHER", result.get("type"));
    }

    @Test
    void shouldConvertGaugeWithNaNAndTags() {
        Gauge gauge = mock(Gauge.class);

        // Meter ID with tags
        Meter.Id id = new Meter.Id(
                "app.info",
                Tags.of(
                        Tag.of("camel.runtime.provider", "Main"),
                        Tag.of("camel.runtime.version", "4.21.0-SNAPSHOT"),
                        Tag.of("camel.context", "camel-1"),
                        Tag.of("camel.version", "4.21.0-SNAPSHOT")),
                null,
                null,
                Meter.Type.GAUGE);

        when(gauge.getId()).thenReturn(id);
        when(gauge.value()).thenReturn(Double.NaN);

        Map<String, Object> result = AbstractMicrometerService.convertMeterToMap(gauge);

        // Assertions
        assertEquals("app.info", result.get("name"));
        assertEquals("gauge", result.get("type"));
        assertTrue(result.get("value") instanceof Double);

        // Check tags
        @SuppressWarnings("unchecked")
        Map<String, String> tags = (Map<String, String>) result.get("tags");
        assertEquals(4, tags.size());
        assertEquals("Main", tags.get("camel.runtime.provider"));
        assertEquals("4.21.0-SNAPSHOT", tags.get("camel.runtime.version"));
        assertEquals("camel-1", tags.get("camel.context"));
        assertEquals("4.21.0-SNAPSHOT", tags.get("camel.version"));
    }

}
