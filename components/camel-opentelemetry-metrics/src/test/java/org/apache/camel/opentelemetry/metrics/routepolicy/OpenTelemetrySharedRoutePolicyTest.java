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

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class OpenTelemetrySharedRoutePolicyTest extends AbstractOpenTelemetryRoutePolicyTest {

    protected Meter meter = otelExtension.getOpenTelemetry().getMeter("meterTest");
    // use a single shared instance of the policy
    protected OpenTelemetryRoutePolicy singletonPolicy = new OpenTelemetryRoutePolicy(null);

    @BindToRegistry("meter")
    public Meter addRegistry() {
        return meter;
    }

    @Test
    public void testSharedPolicy() throws Exception {
        template.request("direct:foo", x -> {});
        template.request("direct:bar", x -> {});

        LongPointData lpd = getSingleLongPointData(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME, "foo");
        assertEquals(2L, lpd.getValue());

        lpd = getSingleLongPointData(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME, "bar");
        assertEquals(2L, lpd.getValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo").routePolicy(singletonPolicy).to("mock:result");

                from("direct:bar").routeId("bar").routePolicy(singletonPolicy).to("mock:result");
            }
        };
    }
}
