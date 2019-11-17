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
import org.eclipse.microprofile.metrics.Histogram;
import org.junit.Test;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_HISTOGRAM_VALUE;

public class MicroProfileMetricsHistogramTest extends MicroProfileMetricsTestSupport {

    @Test
    public void testHistogramMetric() {
        template.sendBody("direct:histogram", null);
        Histogram histogram = getHistogram("test-histogram");
        assertEquals(10, histogram.getSnapshot().getMax());
    }

    @Test
    public void testHistogramMetricHeaderValueSet() {
        template.sendBody("direct:histogramFromHeader", null);
        Histogram histogram = getHistogram("test-histogram-header");
        assertEquals(10, histogram.getSnapshot().getMax());
    }

    @Test
    public void testHistogramMetricHeaderOverrideValueSet() {
        template.sendBodyAndHeader("direct:histogram", null, HEADER_HISTOGRAM_VALUE, 20);
        Histogram histogram = getHistogram("test-histogram");
        assertEquals(20, histogram.getSnapshot().getMax());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:histogram")
                    .to("microprofile-metrics:histogram:test-histogram?value=10");

                from("direct:histogramFromHeader")
                        .setHeader(HEADER_HISTOGRAM_VALUE, constant(10))
                    .to("microprofile-metrics:histogram:test-histogram-header");
            }
        };
    }
}
