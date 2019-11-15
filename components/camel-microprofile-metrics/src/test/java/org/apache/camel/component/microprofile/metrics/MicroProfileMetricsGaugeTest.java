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
import org.apache.camel.component.microprofile.metrics.gauge.SimpleGauge;
import org.junit.Test;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_GAUGE_VALUE;

public class MicroProfileMetricsGaugeTest extends MicroProfileMetricsTestSupport {

    @Test
    public void testGaugeMetric() {
        template.sendBody("direct:gaugeValue", null);
        SimpleGauge gauge = getSimpleGauge("test-gauge");
        assertEquals(10, gauge.getValue().intValue());
    }

    @Test
    public void testGaugeMetricHeaderValue() {
        template.sendBody("direct:gaugeValueHeader", null);
        SimpleGauge gauge = getSimpleGauge("test-gauge-header");
        assertEquals(20, gauge.getValue().intValue());
    }

    @Test
    public void testGaugeMetricHeaderOverrideValue() {
        template.sendBodyAndHeader("direct:gaugeValue", null, HEADER_GAUGE_VALUE, 20);
        SimpleGauge gauge = getSimpleGauge("test-gauge");
        assertEquals(20, gauge.getValue().intValue());
    }

    @Test
    public void testGaugeMetricReuse() {
        template.sendBody("direct:gaugeValue", null);
        SimpleGauge gauge = getSimpleGauge("test-gauge");
        assertEquals(10, gauge.getValue().intValue());

        template.sendBodyAndHeader("direct:gaugeValue", null, HEADER_GAUGE_VALUE, 20);
        gauge = getSimpleGauge("test-gauge");
        assertEquals(20, gauge.getValue().intValue());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:gaugeValue")
                    .to("microprofile-metrics:gauge:test-gauge?gaugeValue=10");

                from("direct:gaugeValueHeader")
                    .setHeader(HEADER_GAUGE_VALUE, constant(20))
                    .to("microprofile-metrics:gauge:test-gauge-header");
            }
        };
    }
}
