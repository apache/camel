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

import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the 'camel.route.policy' metric.
 */
public class OpenTelemetryRoutePolicyTest extends AbstractOpenTelemetryRoutePolicyTest {

    private static final long DELAY_FOO = 20;
    private static final long DELAY_BAR = 50;
    private static final long TOLERANCE = 20L;

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

        PointData pd = getPointDataForRouteId(DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME, "foo");
        verifyHistogramMetric(pd, DELAY_FOO, count / 2);

        pd = getPointDataForRouteId(DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME, "bar");
        verifyHistogramMetric(pd, DELAY_BAR, count / 2);
    }

    private void verifyHistogramMetric(PointData pd, long delay, int msgCount) {
        assertTrue(pd instanceof HistogramPointData);
        HistogramPointData hpd = (HistogramPointData) pd;
        assertTrue(hpd.getMax() < delay + TOLERANCE, "max value");
        assertTrue(hpd.getMin() >= delay, "min value");
        assertEquals(msgCount, hpd.getCount(), "count");
        assertTrue(hpd.getSum() >= msgCount * delay, "sum");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo")
                        .delay(DELAY_FOO)
                        .to("mock:result");

                from("direct:bar").routeId("bar")
                        .delay(DELAY_BAR)
                        .to("mock:result");
            }
        };
    }
}
