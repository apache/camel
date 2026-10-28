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
package org.apache.camel.component.disruptor;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Removing one of the routes that consume from a disruptor endpoint must not shut down the disruptor of the other
 * routes that consume from it, also when they are suspended.
 */
class DisruptorRemoveRouteOfSharedEndpointTest extends CamelTestSupport {

    @Test
    void removingOneOfSeveralConsumersKeepsTheOthersConsuming() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("before", "after");
        getMockEndpoint("mock:b").expectedBodiesReceived("before");
        getMockEndpoint("mock:c").expectedBodiesReceived("before", "after");

        template.sendBody("disruptor:foo?multipleConsumers=true", "before");
        getMockEndpoint("mock:b").assertIsSatisfied();

        context.getRouteController().stopRoute("b");
        context.removeRoute("b");

        template.sendBody("disruptor:foo?multipleConsumers=true", "after");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void removingAConsumerRouteKeepsTheSuspendedOnes() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("after");
        getMockEndpoint("mock:c").expectedBodiesReceived("after");

        context.getRouteController().suspendRoute("a");
        context.getRouteController().suspendRoute("c");
        context.getRouteController().stopRoute("b");
        context.removeRoute("b");
        context.getRouteController().resumeRoute("a");
        context.getRouteController().resumeRoute("c");

        template.sendBody("disruptor:foo?multipleConsumers=true", "after");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void consumerRouteRemovedAndAddedAgainWhileAProducerRouteUsesTheQueue() throws Exception {
        getMockEndpoint("mock:bar").expectedBodiesReceived("before", "after");

        template.sendBody("direct:start", "before");

        // such as a reload of the consumer route
        context.getRouteController().stopRoute("barConsumer");
        context.removeRoute("barConsumer");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("disruptor:bar").routeId("barConsumer").to("mock:bar");
            }
        });

        template.sendBody("direct:start", "after");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("disruptor:foo?multipleConsumers=true").routeId("a").to("mock:a");
                from("disruptor:foo?multipleConsumers=true").routeId("b").to("mock:b");
                from("disruptor:foo?multipleConsumers=true").routeId("c").to("mock:c");

                from("direct:start").routeId("barProducer").to("disruptor:bar");
                from("disruptor:bar").routeId("barConsumer").to("mock:bar");
            }
        };
    }
}
