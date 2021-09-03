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
package org.apache.camel.component.microprofile.metrics.route.policy;

import java.util.SortedMap;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsTestSupport;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MicroProfileMetricsRoutePolicySubRouteTest extends MicroProfileMetricsTestSupport {

    @Test
    public void testMetricsRoutePolicy() throws Exception {
        int count = 10;
        getMockEndpoint("mock:foo").expectedMessageCount(count);
        getMockEndpoint("mock:bar").expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody("direct:foo", null);
        }

        assertMockEndpointsSatisfied();

        SortedMap<MetricID, Timer> timers = metricRegistry.getTimers();
        assertEquals(2, timers.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo")
                        .to("direct:bar")
                        .to("mock:foo");

                from("direct:bar").routeId("bar")
                        .to("mock:bar");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.addRoutePolicyFactory(new MicroProfileMetricsRoutePolicyFactory());
        return camelContext;
    }
}
