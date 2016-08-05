/**
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
package org.apache.camel.component.controlbus;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class ControlBusStatsTest extends ContextTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    public void testControlBusRouteStat() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();

        String xml = template.requestBody("controlbus:route?routeId=foo&action=stats", null, String.class);
        assertNotNull(xml);

        assertTrue(xml.contains("routeStat"));
        assertTrue(xml.contains("processorStat"));
        assertTrue(xml.contains("id=\"foo\""));
        assertTrue(xml.contains("exchangesCompleted=\"1\""));
    }

    public void testControlBusCurrentRouteStat() throws Exception {
        getMockEndpoint("mock:current").expectedBodiesReceived("Hello World");

        template.sendBody("direct:current", "Hello World");

        assertMockEndpointsSatisfied();

        String xml = template.requestBody("controlbus:route?routeId=current&action=stats", null, String.class);
        assertNotNull(xml);

        assertTrue(xml.contains("routeStat"));
        assertTrue(xml.contains("processorStat"));
        assertTrue(xml.contains("id=\"current\""));
        assertTrue(xml.contains("exchangesCompleted=\"1\""));
    }

    public void testControlBusContextStat() throws Exception {
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello World");

        template.sendBody("direct:bar", "Hello World");

        assertMockEndpointsSatisfied();

        String xml = template.requestBody("controlbus:route?action=stats", null, String.class);
        assertNotNull(xml);

        assertTrue(xml.contains("camelContextStat"));
        assertTrue(xml.contains("routeStat"));
        assertTrue(xml.contains("processorStat"));
        assertTrue(xml.contains("id=\"bar\""));
        assertTrue(xml.contains("id=\"foo\""));
        assertTrue(xml.contains("exchangesCompleted=\"1\""));
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo")
                    .to("mock:foo");
                from("direct:bar").routeId("bar")
                    .to("mock:bar");
                from("direct:current").routeId("current")
                    .to("mock:current");
            }
        };
    }
}
