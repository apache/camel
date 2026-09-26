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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.BrowsableVariableRepository;
import org.apache.camel.spi.VariableRepositoryFactory;
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

        template.sendBody("direct:route", "Hello");

        assertMockEndpointsSatisfied();
        // the header variables of route variable resp in route rs are stored with the key header:rs:resp.<header>
        assertEquals("Bye second", context.getVariable("route:rs:resp"));
        assertEquals(200, context.getVariable("route:header:rs:resp.status"));
        assertNull(context.getVariable("route:header:rs:resp.error"));
        assertNoErrorHeader("route");
    }

    @Test
    void testReceiveTwiceGroup() throws Exception {
        getMockEndpoint("mock:group").expectedBodiesReceived("Bye second|200|");

        template.sendBody("direct:group", "Hello");

        assertMockEndpointsSatisfied();
        // the header variables of group variable myGroup:resp are stored with the key header:myGroup:resp.<header>
        assertEquals("Bye second", context.getVariable("group:myGroup:resp"));
        assertEquals(200, context.getVariable("group:header:myGroup:resp.status"));
        assertNull(context.getVariable("group:header:myGroup:resp.error"));
        assertNoErrorHeader("group");
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
                        .setBody(constant("first")).toV("direct:svc", null, "route:resp")
                        .setBody(constant("second")).toV("direct:svc", null, "route:resp")
                        .setBody(simple(
                                "${variable.route:resp}|${variable.route:header:rs:resp.status}|${variable.route:header:rs:resp.error}"))
                        .to("mock:route");

                from("direct:group")
                        .setBody(constant("first")).toV("direct:svc", null, "group:myGroup:resp")
                        .setBody(constant("second")).toV("direct:svc", null, "group:myGroup:resp")
                        .setBody(simple(
                                "${variable.group:myGroup:resp}|${variable.group:header:myGroup:resp.status}|${variable.group:header:myGroup:resp.error}"))
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
