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
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;

import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class OpenTelemetryRoutePolicyMulticastSubRouteTest extends AbstractOpenTelemetryRoutePolicyTest {

    // verify that metrics are recorded on the 'timer' for 'exchange done' events
    @Test
    public void testRouteTimerMeter() {
        int count = 5;
        for (int i = 0; i < count; i++) {
            template.sendBody("direct:multicast", "Hello World");
            template.send("direct:failure", e -> e.getMessage().setBody("Hello World"));
        }
        for (String route : List.of("foo", "bar", "multicast", "failureHandled", "failure")) {
            PointData pd = getPointDataForRouteId(DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME, route);
            assertInstanceOf(HistogramPointData.class, pd);
            HistogramPointData hpd = (HistogramPointData) pd;
            assertEquals(count, hpd.getCount(), "count for route " + route);
        }
    }

    // verify that 'succeeded' counts are recorded
    @Test
    public void testSucceededMeter() {
        int count = 5;
        for (int i = 0; i < count; i++) {
            template.sendBody("direct:multicast", "Hello World");
        }
        for (String route : List.of("foo", "multicast", "bar", "failureHandled")) {
            LongPointData lpd =
                    getSingleLongPointData(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_SUCCEEDED_METER_NAME, route);
            assertEquals(count, lpd.getValue(), "count for route " + route);
        }
    }

    // verify that 'handled failure' counts are recorded
    @Test
    public void testHandledFailuresMeter() {
        int count = 5;
        for (int i = 0; i < count; i++) {
            template.sendBody("direct:multicast", "Hello World");
        }
        for (String route : List.of("multicast", "failureHandled")) {
            LongPointData lpd =
                    getSingleLongPointData(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILURES_HANDLED_METER_NAME, route);
            assertEquals(count, lpd.getValue(), "count for route " + route);
        }
    }

    // verify that 'failed' counts are recorded
    @Test
    public void testFailuresMeter() {
        int count = 5;
        for (int i = 0; i < count; i++) {
            template.send("direct:failure", e -> e.getMessage().setBody("Hello World"));
        }
        LongPointData lpd = getSingleLongPointData(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILED_METER_NAME, "failure");
        assertEquals(count, lpd.getValue(), "count for route failure");
    }

    // verify that 'total' counts are recorded
    @Test
    public void testTotalExchangesMeter() {
        int count = 5;
        for (int i = 0; i < count; i++) {
            template.sendBody("direct:multicast", "Hello World");
            template.send("direct:failure", e -> e.getMessage().setBody("Hello World"));
        }
        for (String route : List.of("foo", "bar", "multicast", "failureHandled", "failure")) {
            LongPointData lpd = getSingleLongPointData(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME, route);
            assertEquals(count, lpd.getValue(), "count for route " + route);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).handled(true);

                from("direct:foo").routeId("foo").to("mock:foo");

                from("direct:bar").routeId("bar").multicast().to("mock:bar1", "mock:bar2");

                from("direct:multicast")
                        .routeId("multicast")
                        .multicast()
                        .to("direct:foo", "direct:bar", "direct:failureHandled");

                from("direct:failure").routeId("failure").throwException(new Exception("forced"));

                from("direct:failureHandled")
                        .routeId("failureHandled")
                        .throwException(new IllegalStateException("forced"));
            }
        };
    }
}
