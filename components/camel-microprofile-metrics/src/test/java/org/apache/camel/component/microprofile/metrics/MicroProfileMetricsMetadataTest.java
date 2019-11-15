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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.metrics.Metadata;
import org.junit.Test;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_METRIC_DESCRIPTION;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_METRIC_DISPLAY_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_METRIC_UNIT;
import static org.eclipse.microprofile.metrics.MetricUnits.KILOBYTES;
import static org.eclipse.microprofile.metrics.MetricUnits.MEGABITS;

public class MicroProfileMetricsMetadataTest extends MicroProfileMetricsTestSupport {

    private static final String METRIC_DISPLAY_NAME = "Test Display Name";
    private static final String METRIC_DISPLAY_NAME_MODIFIED = METRIC_DISPLAY_NAME + " Modified";
    private static final String METRIC_DESCRIPTION = "Test Description";
    private static final String METRIC_DESCRIPTION_MODIFIED = METRIC_DESCRIPTION + " Modified";

    @Test
    public void testMetricMetadata() {
        template.sendBody("direct:metadata", null);
        Metadata metadata = getMetricMetadata("test-counter");
        assertEquals(METRIC_DESCRIPTION, metadata.getDescription().get());
        assertEquals(METRIC_DISPLAY_NAME, metadata.getDisplayName());
        assertEquals(KILOBYTES, metadata.getUnit().get());
    }

    @Test
    public void testMetricMetadataFromHeader() {
        template.sendBody("direct:metadataHeader", null);
        Metadata metadata = getMetricMetadata("test-counter-header");
        assertEquals(METRIC_DESCRIPTION, metadata.getDescription().get());
        assertEquals(METRIC_DISPLAY_NAME, metadata.getDisplayName());
        assertEquals(KILOBYTES, metadata.getUnit().get());
    }

    @Test
    public void testMetricMetadataFromHeadersOverride() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(HEADER_METRIC_DESCRIPTION, METRIC_DISPLAY_NAME_MODIFIED);
        headers.put(HEADER_METRIC_DISPLAY_NAME, METRIC_DESCRIPTION_MODIFIED);
        headers.put(HEADER_METRIC_UNIT, MEGABITS);

        template.sendBodyAndHeaders("direct:metadata", null, headers);
        Metadata metadata = getMetricMetadata("test-counter");
        assertEquals(METRIC_DISPLAY_NAME_MODIFIED, metadata.getDescription().get());
        assertEquals(METRIC_DESCRIPTION_MODIFIED, metadata.getDisplayName());
        assertEquals(MEGABITS, metadata.getUnit().get());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:metadata")
                    .toF("microprofile-metrics:counter:test-counter?description=%s&displayName=%s&metricUnit=%s", METRIC_DESCRIPTION,
                        METRIC_DISPLAY_NAME, KILOBYTES);

                from("direct:metadataHeader")
                    .setHeader(HEADER_METRIC_DESCRIPTION, constant(METRIC_DESCRIPTION))
                    .setHeader(HEADER_METRIC_DISPLAY_NAME, constant(METRIC_DISPLAY_NAME))
                    .setHeader(HEADER_METRIC_UNIT, constant(KILOBYTES))
                    .to("microprofile-metrics:counter:test-counter-header");
            }
        };
    }
}
