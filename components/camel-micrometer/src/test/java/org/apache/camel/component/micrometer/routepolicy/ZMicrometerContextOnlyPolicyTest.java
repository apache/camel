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

package org.apache.camel.component.micrometer.routepolicy;

import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.EVENT_TYPE_TAG;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.Timer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

/**
 * Must run last
 */
public class ZMicrometerContextOnlyPolicyTest extends AbstractMicrometerRoutePolicyTest {

    private static final long DELAY_FOO = 20;
    private static final long DELAY_BAR = 50;

    @Override
    protected MicrometerRoutePolicyFactory createMicrometerRoutePolicyFactory() {
        MicrometerRoutePolicyFactory factory = super.createMicrometerRoutePolicyFactory();
        factory.getPolicyConfiguration().setContextEnabled(true);
        factory.getPolicyConfiguration().setRouteEnabled(false);
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

        Timer contextTimer = meterRegistry
                .find(formatMetricName(DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME))
                .tag(EVENT_TYPE_TAG, "context")
                .timer();
        assertEquals(count, contextTimer.count());
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
