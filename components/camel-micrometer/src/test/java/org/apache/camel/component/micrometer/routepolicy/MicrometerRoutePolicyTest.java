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

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Timer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.ROUTE_ID_TAG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MicrometerRoutePolicyTest extends AbstractMicrometerRoutePolicyTest {

    private static final long DELAY_FOO = 20;
    private static final long DELAY_BAR = 50;

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

        Timer fooTimer
                = meterRegistry.find(formatMetricName(DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME)).tag(ROUTE_ID_TAG, "foo").timer();
        assertEquals(count / 2, fooTimer.count());
        assertTrue(fooTimer.mean(TimeUnit.MILLISECONDS) > DELAY_FOO);
        assertTrue(fooTimer.max(TimeUnit.MILLISECONDS) > DELAY_FOO);
        assertTrue(fooTimer.totalTime(TimeUnit.MILLISECONDS) > DELAY_FOO * count / 2);

        Timer barTimer
                = meterRegistry.find(formatMetricName(DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME)).tag(ROUTE_ID_TAG, "bar").timer();
        assertEquals(count / 2, barTimer.count());
        assertTrue(barTimer.mean(TimeUnit.MILLISECONDS) > DELAY_BAR);
        assertTrue(barTimer.max(TimeUnit.MILLISECONDS) > DELAY_BAR);
        assertTrue(barTimer.totalTime(TimeUnit.MILLISECONDS) > DELAY_BAR * count / 2);

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
