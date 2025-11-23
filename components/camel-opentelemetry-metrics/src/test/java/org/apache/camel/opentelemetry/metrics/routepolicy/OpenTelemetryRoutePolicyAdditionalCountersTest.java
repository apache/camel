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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILED_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILURES_HANDLED_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_SUCCEEDED_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies 'additionalCounters' metrics.
 */
public class OpenTelemetryRoutePolicyAdditionalCountersTest extends AbstractOpenTelemetryRoutePolicyTest {

    @Override
    public OpenTelemetryRoutePolicyFactory createOpenTelemetryRoutePolicyFactory() {
        OpenTelemetryRoutePolicyFactory factory = new OpenTelemetryRoutePolicyFactory();
        // 'additionalCounters' are enabled by default
        factory.getPolicyConfiguration().setContextEnabled(false);
        return factory;
    }

    // verify 'succeeded' counter
    @Test
    public void testNoSucceededCounter() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        template.sendBody("direct:foo", "Hello");

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(1, getSingleLongPointData(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_SUCCEEDED_METER_NAME, "foo").getValue());
    }

    // verify 'total' counter
    @Test
    public void testTotalCounter() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        template.sendBody("direct:foo", "Hello");

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(1, getSingleLongPointData(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME, "foo").getValue());
    }

    // verify 'failed' counter
    @Test
    public void testFailuresMeter() {
        template.send("direct:failure", e -> e.getMessage().setBody("Hello World"));
        assertEquals(1, getSingleLongPointData(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILED_METER_NAME, "failure").getValue());
    }

    // verify 'handled failures' counter
    @Test
    public void testHandledFailuresMeter() {
        template.send("direct:failureHandled", e -> e.getMessage().setBody("Hello World"));
        assertEquals(1,
                getSingleLongPointData(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILURES_HANDLED_METER_NAME, "failureHandled")
                        .getValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class)
                        .handled(true);

                from("direct:foo").routeId("foo")
                        .to("mock:result");

                from("direct:failure").routeId("failure").throwException(new Exception("forced"));

                from("direct:failureHandled").routeId("failureHandled").throwException(new IllegalStateException("forced"));
            }
        };
    }
}
