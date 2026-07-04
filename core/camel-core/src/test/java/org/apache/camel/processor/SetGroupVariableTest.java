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
package org.apache.camel.processor;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SetGroupVariableTest extends ContextTestSupport {

    private MockEndpoint end;

    @Test
    public void testSetGroupVariable() throws Exception {
        assertNull(context.getVariable("group:teamA:foo"));
        assertNull(context.getVariable("group:teamB:foo"));

        end.expectedMessageCount(2);

        template.sendBody("direct:teamA", "<blah/>");
        template.sendBody("direct:teamB", "<blah/>");

        assertMockEndpointsSatisfied();

        // variables should be stored on exchange, not accessible from exchange directly
        List<Exchange> exchanges = end.getExchanges();
        Exchange exchange = exchanges.get(0);
        assertNull(exchange.getVariable("foo"));

        // should be stored as group variables
        assertEquals("bar", context.getVariable("group:teamA:foo"));
        assertEquals("baz", context.getVariable("group:teamB:foo"));
    }

    @Test
    public void testGroupVariableIsolation() throws Exception {
        end.expectedMessageCount(1);

        template.sendBody("direct:teamA", "<blah/>");

        assertMockEndpointsSatisfied();

        // teamA has the variable, teamB does not
        assertEquals("bar", context.getVariable("group:teamA:foo"));
        assertNull(context.getVariable("group:teamB:foo"));
    }

    @Test
    public void testGroupVariableSimpleLanguage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Value is bar");

        template.sendBody("direct:simple", "<blah/>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCrossRouteGroupVariable() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:cross");
        mock.expectedBodiesReceived("shared-value");

        // route "setter" sets the group variable, route "reader" reads it
        template.sendBody("direct:setter", "<blah/>");
        template.sendBody("direct:reader", "<blah/>");

        assertMockEndpointsSatisfied();
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        end = getMockEndpoint("mock:end");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:teamA").routeId("routeA")
                        .setVariable("group:teamA:foo").constant("bar")
                        .to("mock:end");

                from("direct:teamB").routeId("routeB")
                        .setVariable("group:teamB:foo").constant("baz")
                        .to("mock:end");

                from("direct:simple").routeId("routeSimple")
                        .setVariable("group:teamA:foo").constant("bar")
                        .transform().simple("Value is ${variable.group:teamA:foo}")
                        .to("mock:result");

                from("direct:setter").routeId("routeSetter")
                        .setVariable("group:shared:myKey").constant("shared-value")
                        .to("mock:end");

                from("direct:reader").routeId("routeReader")
                        .setBody().variable("group:shared:myKey")
                        .to("mock:cross");
            }
        };
    }
}
