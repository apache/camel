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
import java.util.concurrent.TimeUnit;

import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_TASKS_ACTIVE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_TASKS_DURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that there are point data for the context and route when context metrics are enabled.
 */
public class OpenTelemetryContextMetricTest extends AbstractOpenTelemetryRoutePolicyTest {

    private static final long DELAY = 1500L;
    private static final long TOLERANCE = 100L;

    @Override
    public OpenTelemetryRoutePolicyFactory createOpenTelemetryRoutePolicyFactory() {
        OpenTelemetryRoutePolicyFactory factory = new OpenTelemetryRoutePolicyFactory();
        factory.getPolicyConfiguration().setLongTask(true);
        return factory;
    }

    // verify maximum duration of a long task
    @Test
    public void testLongTaskDuration() throws Exception {
        MockEndpoint out = getMockEndpoint("mock:foo");
        out.expectedMessageCount(1);

        template.asyncSend("direct:foo", x -> {
        });

        long maxDuration = pollLongTimer(DEFAULT_CAMEL_ROUTE_POLICY_TASKS_DURATION);
        assertTrue(maxDuration >= 0L && maxDuration < DELAY + TOLERANCE, "max duration of long task");
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(
                () -> MockEndpoint.assertIsSatisfied(context));
    }

    // verify maximum number of concurrent active long tasks
    @Test
    public void testLongTaskActive() throws Exception {
        final int messageCnt = 2;
        MockEndpoint out = getMockEndpoint("mock:foo");
        out.expectedMessageCount(messageCnt);

        for (int i = 0; i < messageCnt; i++) {
            template.asyncSend("direct:foo", x -> {
            });
        }
        long maxActive = pollLongTimer(DEFAULT_CAMEL_ROUTE_POLICY_TASKS_ACTIVE);
        assertEquals(messageCnt, maxActive, "max active long tasks");
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(
                () -> MockEndpoint.assertIsSatisfied(context));
    }

    // verifies that there are point data for the context and route
    private long pollLongTimer(String meterName) throws Exception {
        Thread.sleep(250L);
        long max = 0L;
        long curr = 0L;
        for (int i = 0; i < 10; i++) {
            Thread.sleep(250L);

            List<PointData> ls = getAllPointData(meterName);
            // counts from context and route statistics
            assertEquals(2, ls.size(), "Expected two point data");

            for (var pd : ls) {
                assertInstanceOf(LongPointData.class, pd);
                LongPointData lpd = (LongPointData) pd;
                curr = lpd.getValue();
                max = Math.max(max, curr);
            }
            if (curr == 0L) {
                break;
            }
        }
        return max;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo").delay(DELAY).to("mock:foo");
            }
        };
    }
}
