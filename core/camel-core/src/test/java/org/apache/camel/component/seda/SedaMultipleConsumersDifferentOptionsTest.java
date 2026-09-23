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
package org.apache.camel.component.seda;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

/**
 * With multipleConsumers=true every consumer of the queue must receive a copy of each message, also when the consumer
 * endpoints use the same queue name with different uri options.
 */
public class SedaMultipleConsumersDifferentOptionsTest extends ContextTestSupport {

    private static final int MESSAGES = 20;

    @Test
    public void testEachConsumerReceivesEveryMessage() throws Exception {
        String[] bodies = bodies("Hello");
        getMockEndpoint("mock:a").expectedBodiesReceivedInAnyOrder((Object[]) bodies);
        getMockEndpoint("mock:b").expectedBodiesReceivedInAnyOrder((Object[]) bodies);
        getMockEndpoint("mock:c").expectedBodiesReceivedInAnyOrder((Object[]) bodies);

        for (String body : bodies) {
            template.sendBody("seda:news", body);
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testStoppedConsumer() throws Exception {
        context.getRouteController().stopRoute("b");

        String[] bodies = bodies("Bye");
        getMockEndpoint("mock:a").expectedBodiesReceivedInAnyOrder((Object[]) bodies);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedBodiesReceivedInAnyOrder((Object[]) bodies);

        for (String body : bodies) {
            template.sendBody("seda:news", body);
        }

        assertMockEndpointsSatisfied();

        // and when started again it receives the messages sent from now on as well
        context.getRouteController().startRoute("b");
        resetMocks();

        bodies = bodies("Again");
        getMockEndpoint("mock:a").expectedBodiesReceivedInAnyOrder((Object[]) bodies);
        getMockEndpoint("mock:b").expectedBodiesReceivedInAnyOrder((Object[]) bodies);
        getMockEndpoint("mock:c").expectedBodiesReceivedInAnyOrder((Object[]) bodies);

        for (String body : bodies) {
            template.sendBody("seda:news", body);
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testConsumerAddedWithOtherOptions() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:news?multipleConsumers=true&concurrentConsumers=3").routeId("d").to("mock:d");
            }
        });

        String[] bodies = bodies("Hi");
        for (String name : new String[] { "mock:a", "mock:b", "mock:c", "mock:d" }) {
            getMockEndpoint(name).expectedBodiesReceivedInAnyOrder((Object[]) bodies);
        }

        for (String body : bodies) {
            template.sendBody("seda:news", body);
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    private static String[] bodies(String prefix) {
        String[] bodies = new String[MESSAGES];
        for (int i = 0; i < MESSAGES; i++) {
            bodies[i] = prefix + " " + i;
        }
        return bodies;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:news?multipleConsumers=true").routeId("a").to("mock:a");
                from("seda:news?multipleConsumers=true&concurrentConsumers=2").routeId("b").to("mock:b");
                from("seda:news?multipleConsumers=true&pollTimeout=200").routeId("c").to("mock:c");
            }
        };
    }
}
