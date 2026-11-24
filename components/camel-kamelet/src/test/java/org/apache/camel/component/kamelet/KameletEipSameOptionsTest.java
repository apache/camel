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
package org.apache.camel.component.kamelet;

import java.util.concurrent.TimeUnit;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Kamelet EIPs with the same name and options each continue with their own steps, and a to("kamelet:...") with the same
 * name and options does not run the steps of an EIP.
 */
class KameletEipSameOptionsTest extends CamelTestSupport {

    @Test
    void eachKameletEipContinuesWithItsOwnSteps() throws Exception {
        getMockEndpoint("mock:b1-nested").expectedBodiesReceived("from-b1");
        getMockEndpoint("mock:b2-nested").expectedBodiesReceived("from-b2");

        template.sendBody("direct:b1", "from-b1");
        template.sendBody("direct:b2", "from-b2");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void toKameletDoesNotRunTheStepsOfAKameletEip() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("from-a");
        getMockEndpoint("mock:a").expectedHeaderReceived("action", "done");
        getMockEndpoint("mock:b1-nested").expectedMessageCount(0);
        getMockEndpoint("mock:b2-nested").expectedMessageCount(0);

        template.sendBody("direct:a", "from-a");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void removingTheRouteOfAKameletEipRemovesItsKameletRoute() throws Exception {
        int before = context.getRoutes().size();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:c").routeId("c")
                        .kamelet("my-action?value=done")
                        .to("mock:c-nested")
                        .end();
            }
        });
        // the route and the Kamelet route of its EIP
        assertEquals(before + 2, context.getRoutes().size());

        getMockEndpoint("mock:c-nested").expectedBodiesReceived("from-c");
        template.sendBody("direct:c", "from-c");
        MockEndpoint.assertIsSatisfied(context);

        context.getRouteController().stopRoute("c");
        context.removeRoute("c");
        // the Kamelet route of the EIP is removed with it, a moment later
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(before, context.getRoutes().size()));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("my-action")
                        .templateParameter("value")
                        .from("kamelet:source")
                        .setHeader("action", constant("{{value}}"))
                        .to("kamelet:sink");

                from("direct:b1").routeId("b1")
                        .kamelet("my-action?value=done")
                        .to("mock:b1-nested")
                        .end();

                from("direct:b2").routeId("b2")
                        .kamelet("my-action?value=done")
                        .to("mock:b2-nested")
                        .end();

                from("direct:a").routeId("a")
                        .to("kamelet:my-action?value=done")
                        .to("mock:a");
            }
        };
    }
}
