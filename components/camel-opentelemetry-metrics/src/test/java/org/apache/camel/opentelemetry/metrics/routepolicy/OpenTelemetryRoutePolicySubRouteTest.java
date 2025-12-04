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

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_SUCCEEDED_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class OpenTelemetryRoutePolicySubRouteTest extends AbstractOpenTelemetryRoutePolicyTest {

    @Test
    public void testTotalMetric() throws Exception {
        int count = 5;
        getMockEndpoint("mock:foo").expectedMessageCount(count);
        getMockEndpoint("mock:bar").expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody("direct:foo", "Hello World");
        }
        MockEndpoint.assertIsSatisfied(context);

        for (String routeId : List.of("foo", "bar")) {
            PointData pd = getPointDataForRouteId(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME, routeId);
            assertNotNull(pd, "No metric found for routeId " + routeId);
            assertEquals(count, ((LongPointData) pd).getValue(), "Value for routeId " + routeId);
        }
    }

    @Test
    public void testSucceededMetric() throws Exception {
        int count = 5;
        for (int i = 0; i < count; i++) {
            template.sendBody("direct:foo", "Hello World");
        }
        MockEndpoint.assertIsSatisfied(context);

        for (String routeId : List.of("foo", "bar")) {
            PointData pd = getPointDataForRouteId(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_SUCCEEDED_METER_NAME, routeId);
            assertNotNull(pd, "No metric found for routeId " + routeId);
            assertEquals(count, ((LongPointData) pd).getValue(), "Value for routeId " + routeId);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo").to("direct:bar").to("mock:foo");

                from("direct:bar").routeId("bar").to("mock:bar");
            }
        };
    }
}
