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

package org.apache.camel.opentelemetry.metrics.routepolicy;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.EVENT_TYPE_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.KIND_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.ROUTE_ID_ATTRIBUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class OpenTelemetryContextPolicyTest extends AbstractOpenTelemetryRoutePolicyTest {

    private static final long DELAY_FOO = 20L;
    private static final long DELAY_BAR = 50L;
    private static final long TOLERANCE = 20L;

    @Override
    public OpenTelemetryRoutePolicyFactory createOpenTelemetryRoutePolicyFactory() {
        OpenTelemetryRoutePolicyFactory factory = new OpenTelemetryRoutePolicyFactory();
        factory.getPolicyConfiguration().setContextEnabled(true);
        return factory;
    }

    @Test
    public void testMetricsRoutePolicy() throws Exception {
        int count = 10;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                template.sendBody("direct:foo", "Hello " + i);
            } else {
                template.sendBody("direct:bar", "Hello " + i);
            }
        }
        MockEndpoint.assertIsSatisfied(context);

        // metrics for routes
        verifyMetricForRouteId("foo", DELAY_FOO, DELAY_FOO + TOLERANCE, count / 2);
        verifyMetricForRouteId("bar", DELAY_BAR, DELAY_BAR + TOLERANCE, count / 2);

        // metric for context, min and max delay of all routes
        verifyMetricForRouteId("", DELAY_FOO, DELAY_BAR + TOLERANCE, count);
    }

    private void verifyMetricForRouteId(String routeId, long minDelay, long maxDelay, int count) {
        List<PointData> pdList = getAllPointDataForRouteId(DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME, routeId);
        assertEquals(1, pdList.size());

        PointData pd = pdList.get(0);
        assertEquals(routeId, pd.getAttributes().get(stringKey(ROUTE_ID_ATTRIBUTE)));
        assertEquals("CamelRoute", pd.getAttributes().get(stringKey(KIND_ATTRIBUTE)));
        assertEquals(
                "".equals(routeId) ? "context" : "route", pd.getAttributes().get(stringKey(EVENT_TYPE_ATTRIBUTE)));

        assertInstanceOf(HistogramPointData.class, pd);
        HistogramPointData hpd = (HistogramPointData) pd;
        assertTrue(hpd.getMax() < maxDelay);
        assertTrue(hpd.getMin() >= minDelay);
        assertEquals(count, hpd.getCount());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo").delay(DELAY_FOO).to("mock:result");

                from("direct:bar").routeId("bar").delay(DELAY_BAR).to("mock:result");
            }
        };
    }
}
