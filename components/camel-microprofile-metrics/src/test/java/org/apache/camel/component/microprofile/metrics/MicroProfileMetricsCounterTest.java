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
import org.eclipse.microprofile.metrics.Counter;
import org.junit.Test;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_COUNTER_INCREMENT;

public class MicroProfileMetricsCounterTest extends MicroProfileMetricsTestSupport {

    @Test
    public void testCounterMetric() {
        template.sendBody("direct:increment", null);
        Counter counter = getCounter("test-counter");
        assertEquals(10, counter.getCount());
    }

    @Test
    public void testCounterMetricDefaultIncrement() {
        template.sendBody("direct:default", null);
        Counter counter = getCounter("test-counter");
        assertEquals(1, counter.getCount());
    }

    @Test
    public void testCounterMetricIsDefaultType() {
        template.sendBody("direct:defaultMetricType", null);
        Counter counter = getCounter("test-counter");
        assertEquals(1, counter.getCount());
    }

    @Test
    public void testCounterMetricHeaderValueIncrement() {
        template.sendBody("direct:incrementFromHeader", null);
        Counter counter = getCounter("test-counter-header");
        assertEquals(10, counter.getCount());
    }

    @Test
    public void testCounterMetricHeaderOverrideValueIncrement() {
        template.sendBodyAndHeader("direct:increment", null, HEADER_COUNTER_INCREMENT, 20);
        Counter counter = getCounter("test-counter");
        assertEquals(20, counter.getCount());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:increment")
                    .to("microprofile-metrics:counter:test-counter?counterIncrement=10");

                from("direct:default")
                    .to("microprofile-metrics:counter:test-counter");

                from("direct:defaultMetricType")
                    .to("microprofile-metrics:test-counter");

                from("direct:incrementFromHeader")
                    .setHeader(HEADER_COUNTER_INCREMENT, constant(10))
                    .to("microprofile-metrics:counter:test-counter-header");
            }
        };
    }
}
