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

import java.util.List;

import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_TASKS_ACTIVE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_TASKS_DURATION;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.EVENT_TYPE_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.KIND_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.ROUTE_ID_ATTRIBUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that there are only context point data when only context metrics are enabled.
 */
public class OpenTelemetryContextOnlyPolicyTest extends AbstractOpenTelemetryRoutePolicyTest {

    private static final long DELAY_FOO = 20;
    private static final long DELAY_BAR = 50;
    private static final long DELAY_BAZ = 1500L;
    private static final long TOLERANCE = 20L;

    @Override
    public OpenTelemetryRoutePolicyFactory createOpenTelemetryRoutePolicyFactory() {
        OpenTelemetryRoutePolicyFactory factory = new OpenTelemetryRoutePolicyFactory();
        factory.getPolicyConfiguration().setContextEnabled(true);
        factory.getPolicyConfiguration().setRouteEnabled(false);
        factory.getPolicyConfiguration().setLongTask(true);
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

        // metrics for context only
        List<PointData> pointDataList = getAllPointData(DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME);
        assertEquals(1, pointDataList.size());

        PointData pd = pointDataList.get(0);
        assertEquals("", pd.getAttributes().get(stringKey(ROUTE_ID_ATTRIBUTE)));
        assertEquals("CamelRoute", pd.getAttributes().get(stringKey(KIND_ATTRIBUTE)));
        assertEquals("context", pd.getAttributes().get(stringKey(EVENT_TYPE_ATTRIBUTE)));

        assertInstanceOf(HistogramPointData.class, pd);
        HistogramPointData hpd = (HistogramPointData) pd;
        assertTrue(hpd.getMax() < DELAY_BAR + TOLERANCE);
        assertTrue(hpd.getMin() >= DELAY_FOO);
        assertEquals(count, hpd.getCount());
    }

    @Test
    public void testLongTaskDuration() throws Exception {
        MockEndpoint out = getMockEndpoint("mock:baz");
        out.expectedMessageCount(1);

        template.asyncSend("direct:baz", x -> {
        });

        long maxDuration = pollLongTimer(DEFAULT_CAMEL_ROUTE_POLICY_TASKS_DURATION);
        assertTrue(maxDuration >= 0L && maxDuration < DELAY_BAZ + TOLERANCE, "max duration of long task");

        MockEndpoint.assertIsSatisfied(context);
    }

    // verify maximum number of concurrent active long tasks
    @Test
    public void testLongTaskActive() throws Exception {
        final int messageCnt = 2;
        MockEndpoint out = getMockEndpoint("mock:baz");
        out.expectedMessageCount(messageCnt);

        for (int i = 0; i < messageCnt; i++) {
            template.asyncSend("direct:baz", x -> {
            });
        }
        long maxActive = pollLongTimer(DEFAULT_CAMEL_ROUTE_POLICY_TASKS_ACTIVE);
        assertEquals(messageCnt, maxActive, "max active long tasks");

        MockEndpoint.assertIsSatisfied(context);
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

                from("direct:baz").routeId("baz").delay(DELAY_BAZ).to("mock:baz");
            }
        };
    }
}
