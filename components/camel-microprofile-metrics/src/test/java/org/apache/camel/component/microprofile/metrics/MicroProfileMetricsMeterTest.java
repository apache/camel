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
import org.eclipse.microprofile.metrics.Meter;
import org.junit.Test;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_METER_MARK;

public class MicroProfileMetricsMeterTest extends MicroProfileMetricsTestSupport {

    @Test
    public void testMeterMetric() {
        template.sendBody("direct:mark", null);
        Meter meter = getMeter("test-meter");
        assertEquals(10, meter.getCount());
    }

    @Test
    public void testMeterMetricDefaultMarkValue() {
        template.sendBody("direct:default", null);
        Meter meter = getMeter("test-meter");
        assertEquals(1, meter.getCount());
    }

    @Test
    public void testMeterMetricHeaderMarkValue() {
        template.sendBody("direct:markFromHeader", null);
        Meter meter = getMeter("test-meter-header");
        assertEquals(10, meter.getCount());
    }

    @Test
    public void testMeterMetricOverrideHeaderMarkValue() {
        template.sendBodyAndHeader("direct:mark", null, HEADER_METER_MARK, 20);
        Meter meter = getMeter("test-meter");
        assertEquals(20, meter.getCount());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:mark")
                    .to("microprofile-metrics:meter:test-meter?mark=10");

                from("direct:default")
                    .to("microprofile-metrics:meter:test-meter");

                from("direct:markFromHeader")
                    .setHeader(HEADER_METER_MARK, constant(10))
                    .to("microprofile-metrics:meter:test-meter-header");
            }
        };
    }
}
