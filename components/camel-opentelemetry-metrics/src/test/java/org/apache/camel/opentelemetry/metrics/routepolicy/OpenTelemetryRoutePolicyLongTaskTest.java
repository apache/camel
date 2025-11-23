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

import io.opentelemetry.sdk.metrics.data.MetricData;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_TASKS_ACTIVE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_TASKS_DURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that there are point data for the 'long task' timers when long task metrics are enabled.
 */
public class OpenTelemetryRoutePolicyLongTaskTest extends AbstractOpenTelemetryRoutePolicyTest {

    private static final long DELAY = 1500L;
    private static final long TOLERANCE = 100L;

    @Override
    public OpenTelemetryRoutePolicyFactory createOpenTelemetryRoutePolicyFactory() {
        OpenTelemetryRoutePolicyFactory factory = new OpenTelemetryRoutePolicyFactory();
        factory.getPolicyConfiguration().setContextEnabled(false);
        factory.getPolicyConfiguration().setLongTask(true);
        return factory;
    }

    // verify maximum duration of a long task using metric name 'camel.route.policy.long.task.duration'
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

    // verify maximum number of concurrent active long tasks using metric name 'camel.route.policy.long.task.active'
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

        MockEndpoint.assertIsSatisfied(context);
    }

    // verifies that there are metrics for the 'long task' timer for no messaging
    @Test
    public void testLongTaskMetricData() {
        List<MetricData> mdList = getMetricData(DEFAULT_CAMEL_ROUTE_POLICY_TASKS_DURATION);
        assertFalse(mdList.isEmpty());
        MetricData md = mdList.get(0);
        assertEquals(DEFAULT_CAMEL_ROUTE_POLICY_TASKS_DURATION, md.getName());
        assertEquals("Route long task duration metric", md.getDescription());

        mdList = getMetricData(DEFAULT_CAMEL_ROUTE_POLICY_TASKS_ACTIVE);
        assertFalse(mdList.isEmpty());
        md = mdList.get(0);
        assertEquals(DEFAULT_CAMEL_ROUTE_POLICY_TASKS_ACTIVE, md.getName());
        assertEquals("Route active long task metric", md.getDescription());
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
