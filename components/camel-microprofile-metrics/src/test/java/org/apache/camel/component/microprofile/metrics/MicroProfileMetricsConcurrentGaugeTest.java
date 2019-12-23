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
package org.apache.camel.component.microprofile.metrics;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.junit.Test;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_GAUGE_DECREMENT;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_GAUGE_INCREMENT;

public class MicroProfileMetricsConcurrentGaugeTest extends MicroProfileMetricsTestSupport {

    @Test
    public void testGaugeMetric() {
        template.sendBody("direct:gaugeIncrement", null);
        ConcurrentGauge gauge = getConcurrentGauge("test-gauge");
        assertEquals(1, gauge.getCount());
        template.sendBody("direct:gaugeDecrement", null);
        assertEquals(0, gauge.getCount());
    }

    @Test
    public void testGaugeMetricHeaderIncrementDecrement() {
        template.sendBody("direct:gaugeIncrementHeader", null);
        ConcurrentGauge gauge = getConcurrentGauge("test-gauge-header");
        assertEquals(1, gauge.getCount());
        template.sendBody("direct:gaugeDecrementHeader", null);
        assertEquals(0, gauge.getCount());
    }

    @Test
    public void testCounterMetricHeaderOverrideIncrement() {
        template.sendBodyAndHeader("direct:gaugeIncrement", null, HEADER_GAUGE_INCREMENT, false);
        ConcurrentGauge gauge = getConcurrentGauge("test-gauge");
        assertEquals(0, gauge.getCount());
    }

    @Test
    public void testCounterMetricHeaderOverrideDecrement() {
        template.sendBody("direct:gaugeIncrement", null);
        ConcurrentGauge gauge = getConcurrentGauge("test-gauge");
        assertEquals(1, gauge.getCount());
        template.sendBodyAndHeader("direct:gaugeDecrement", null, HEADER_GAUGE_DECREMENT, false);
        assertEquals(1, gauge.getCount());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:gaugeIncrement")
                    .to("microprofile-metrics:concurrent gauge:test-gauge?gaugeIncrement=true");

                from("direct:gaugeDecrement")
                    .to("microprofile-metrics:concurrent gauge:test-gauge?gaugeDecrement=true");

                from("direct:gaugeIncrementHeader")
                    .setHeader(HEADER_GAUGE_INCREMENT, constant(true))
                    .to("microprofile-metrics:concurrent gauge:test-gauge-header");

                from("direct:gaugeDecrementHeader")
                    .setHeader(HEADER_GAUGE_DECREMENT, constant(true))
                    .to("microprofile-metrics:concurrent gauge:test-gauge-header");
            }
        };
    }
}
