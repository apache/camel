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

import java.util.Set;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.BrowsableVariableRepository;
import org.apache.camel.spi.VariableRepositoryFactory;
import org.apache.camel.support.GroupVariableRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Receiving into a variable again replaces all its header variables, so a header that the new reply does not have is
 * not kept with the value from the previous reply.
 */
class ToVariableReceiveHeadersTest extends ContextTestSupport {

    @Test
    void testReceiveTwice() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:local");
        mock.expectedVariableReceived("resp", "Bye second");
        mock.expectedVariableReceived("header:resp.status", 200);
        mock.message(0).variable("header:resp.error").isNull();
        // the headers of another variable are kept
        mock.expectedVariableReceived("other", "Bye first");
        mock.expectedVariableReceived("header:other.error", "E42");

        template.sendBody("direct:local", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testReceiveTwiceGlobal() throws Exception {
        getMockEndpoint("mock:global").expectedBodiesReceived("Bye first|500|E42", "Bye second|200|");

        template.sendBody("direct:global", "first");
        template.sendBody("direct:global", "second");

        assertMockEndpointsSatisfied();
        assertEquals(200, context.getVariable("global:header:resp.status"));
        assertNull(context.getVariable("global:header:resp.error"));
        assertNoErrorHeader("global");
    }

    @Test
    void testReceiveTwiceRoute() throws Exception {
        getMockEndpoint("mock:route").expectedBodiesReceived("Bye second|200|");

        // route variable resp of another route, which must be kept
        template.sendBody("direct:route2", "Hello");
        template.sendBody("direct:route", "Hello");

        assertMockEndpointsSatisfied();
        // the header variables of route variable resp are stored in route rs, as header:resp.<header>
        assertEquals("Bye second", context.getVariable("route:rs:resp"));
        assertEquals(200, context.getVariable("route:rs:header:resp.status"));
        assertNull(context.getVariable("route:rs:header:resp.error"));
        // the header variables of another variable of the route, and of the same variable in another route, are kept
        assertEquals("E42", context.getVariable("route:rs:header:other.error"));
        assertEquals("E42", context.getVariable("route:rs2:header:resp.error"));
        assertEquals("Bye first", context.getVariable("route:rs2:resp"));
        // no route with the id header
        assertNull(context.getVariable("route:header:rs:resp.status"));
        assertNoRouteOrGroupNamedHeader("route");
    }

    @Test
    void testRemoveRouteRemovesHeaderVariables() throws Exception {
        template.sendBody("direct:route2", "Hello");
        assertEquals(500, context.getVariable("route:rs2:header:resp.status"));

        context.getRouteController().stopRoute("rs2");
        context.removeRoute("rs2");

        assertNull(context.getVariable("route:rs2:resp"));
        assertNull(context.getVariable("route:rs2:header:resp.status"));
        assertNull(context.getVariable("route:rs2:header:resp.error"));
    }

    @Test
    void testReceiveTwiceGroup() throws Exception {
        getMockEndpoint("mock:group").expectedBodiesReceived("Bye second|200|");

        template.sendBody("direct:group", "Hello");

        assertMockEndpointsSatisfied();
        // the header variables of group variable myGroup:resp are stored in group myGroup, as header:resp.<header>
        assertEquals("Bye second", context.getVariable("group:myGroup:resp"));
        assertEquals(200, context.getVariable("group:myGroup:header:resp.status"));
        assertNull(context.getVariable("group:myGroup:header:resp.error"));
        // the header variables of another variable of the group, and of the same variable in another group, are kept
        assertEquals("E42", context.getVariable("group:myGroup:header:other.error"));
        assertEquals("E42", context.getVariable("group:otherGroup:header:resp.error"));
        assertEquals("Bye first", context.getVariable("group:otherGroup:resp"));
        // no group with the id header
        assertNull(context.getVariable("group:header:myGroup:resp.status"));
        assertNoRouteOrGroupNamedHeader("group");
        GroupVariableRepository repo = (GroupVariableRepository) context.getCamelContextExtension()
                .getContextPlugin(VariableRepositoryFactory.class).getVariableRepository("group");
        assertEquals(Set.of("myGroup", "otherGroup"), repo.getGroupIds());
    }

    private void assertNoRouteOrGroupNamedHeader(String id) {
        BrowsableVariableRepository repo = (BrowsableVariableRepository) context.getCamelContextExtension()
                .getContextPlugin(VariableRepositoryFactory.class).getVariableRepository(id);
        assertFalse(repo.getVariables().keySet().stream().anyMatch(k -> k.startsWith("header:")),
                "header variables under the id header in " + id + " repository: " + repo.getVariables().keySet());
    }

    private void assertNoErrorHeader(String id) {
        BrowsableVariableRepository repo = (BrowsableVariableRepository) context.getCamelContextExtension()
                .getContextPlugin(VariableRepositoryFactory.class).getVariableRepository(id);
        assertFalse(repo.getVariables().keySet().stream().anyMatch(k -> k.endsWith("resp.error")),
                "stale header variable in " + id + " repository: " + repo.getVariables().keySet());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:local")
                        .setBody(constant("first")).toV("direct:svc", null, "other")
                        .setBody(constant("first")).toV("direct:svc", null, "resp")
                        .setBody(constant("second")).toV("direct:svc", null, "resp")
                        .to("mock:local");

                from("direct:global")
                        .toV("direct:svc", null, "global:resp")
                        .setBody(simple(
                                "${variable.global:resp}|${variable.global:header:resp.status}|${variable.global:header:resp.error}"))
                        .to("mock:global");

                from("direct:route").routeId("rs")
                        .setBody(constant("first")).toV("direct:svc", null, "route:other")
                        .setBody(constant("first")).toV("direct:svc", null, "route:resp")
                        .setBody(constant("second")).toV("direct:svc", null, "route:resp")
                        .setBody(simple(
                                "${variable.route:resp}|${variable.route:header:resp.status}|${variable.route:header:resp.error}"))
                        .to("mock:route");

                from("direct:route2").routeId("rs2")
                        .setBody(constant("first")).toV("direct:svc", null, "route:resp");

                from("direct:group")
                        .setBody(constant("first")).toV("direct:svc", null, "group:otherGroup:resp")
                        .setBody(constant("first")).toV("direct:svc", null, "group:myGroup:other")
                        .setBody(constant("first")).toV("direct:svc", null, "group:myGroup:resp")
                        .setBody(constant("second")).toV("direct:svc", null, "group:myGroup:resp")
                        .setBody(simple(
                                "${variable.group:myGroup:resp}|${variable.group:myGroup:header:resp.status}|${variable.group:myGroup:header:resp.error}"))
                        .to("mock:group");

                // only the reply to the first request has the error header
                from("direct:svc")
                        .choice()
                            .when(body().isEqualTo("first"))
                                .setHeader("error", constant("E42"))
                                .setHeader("status", constant(500))
                            .otherwise()
                                .setHeader("status", constant(200))
                        .end()
                        .transform().simple("Bye ${body}");
            }
        };
    }
}
