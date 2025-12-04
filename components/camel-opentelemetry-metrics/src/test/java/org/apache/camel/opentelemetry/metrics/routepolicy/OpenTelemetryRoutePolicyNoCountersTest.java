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

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILED_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILURES_HANDLED_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_SUCCEEDED_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

/**
 * Verifies that no additional counters are created when 'additionalCounters' is disabled.
 */
public class OpenTelemetryRoutePolicyNoCountersTest extends AbstractOpenTelemetryRoutePolicyTest {

    @Override
    public OpenTelemetryRoutePolicyFactory createOpenTelemetryRoutePolicyFactory() {
        OpenTelemetryRoutePolicyFactory factory = new OpenTelemetryRoutePolicyFactory();
        factory.getPolicyConfiguration().setContextEnabled(false);
        factory.getPolicyConfiguration().setAdditionalCounters(false);
        return factory;
    }

    // verify no 'succeeded' meter name since 'additionalCounters' is false
    @Test
    public void testNoSucceededCounter() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        template.sendBody("direct:foo", "Hello");

        MockEndpoint.assertIsSatisfied(context);
        assertTrue(getMetricData(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_SUCCEEDED_METER_NAME)
                .isEmpty());
    }

    // verify no 'total' meter name since 'additionalCounters' is false
    @Test
    public void testNoTotalCounter() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        template.sendBody("direct:foo", "Hello");

        MockEndpoint.assertIsSatisfied(context);
        assertTrue(getMetricData(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME)
                .isEmpty());
    }

    // verify no 'failed' meter name since 'additionalCounters' is false
    @Test
    public void testNoFailuresMeter() {
        int count = 3;
        for (int i = 0; i < count; i++) {
            template.send("direct:failure", e -> e.getMessage().setBody("Hello World"));
        }
        assertTrue(getMetricData(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILED_METER_NAME)
                .isEmpty());
    }

    // verify no 'handled failures' meter name since 'additionalCounters' is false
    @Test
    public void testNoHandledFailuresMeter() {
        int count = 3;
        for (int i = 0; i < count; i++) {
            template.send("direct:failureHandled", e -> e.getMessage().setBody("Hello World"));
        }
        assertTrue(getMetricData(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILURES_HANDLED_METER_NAME)
                .isEmpty());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).handled(true);

                from("direct:foo").routeId("foo").to("mock:result");

                from("direct:failure").routeId("failure").throwException(new Exception("forced"));

                from("direct:failureHandled")
                        .routeId("failureHandled")
                        .throwException(new IllegalStateException("forced"));
            }
        };
    }
}
