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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * This test suite is testing different scenarios where a disruptor is forced to
 * buffer exchanges locally until a consumer is registered.
 */
public class DisruptorBufferingTest extends CamelTestSupport {

    @Test
    public void testDisruptorBufferingWhileWaitingOnFirstConsumer() throws Exception {
        template.sendBody("disruptor:foo", "A");
        template.sendBody("disruptor:foo", "B");
        template.sendBody("disruptor:foo", "C");

        final DisruptorEndpoint disruptorEndpoint = getMandatoryEndpoint("disruptor:foo",
                DisruptorEndpoint.class);

        assertEquals(5, disruptorEndpoint.getDisruptor().getRemainingCapacity());

        // Add a first consumer on the endpoint
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("disruptor:foo").routeId("bar").to("mock:bar");
            }
        });

        // Now that we have a consumer, the disruptor should send the buffered
        // events downstream. Expect to receive the 3 original exchanges.
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:bar");
        mockEndpoint.expectedMessageCount(3);
        mockEndpoint.assertIsSatisfied(200);
    }

    @Test
    public void testDisruptorBufferingWhileWaitingOnNextConsumer() throws Exception {
        template.sendBody("disruptor:foo", "A");
        template.sendBody("disruptor:foo", "B");
        template.sendBody("disruptor:foo", "C");

        final DisruptorEndpoint disruptorEndpoint = getMandatoryEndpoint("disruptor:foo",
                DisruptorEndpoint.class);

        assertEquals(5, disruptorEndpoint.getDisruptor().getRemainingCapacity());

        // Add a first consumer on the endpoint
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("disruptor:foo").routeId("bar1").delay(200).to("mock:bar");
            }
        });

        // Now that we have a consumer, the disruptor should send the buffered
        // events downstream. Wait until we have processed at least one
        // exchange.
        MockEndpoint mockEndpoint = getMockEndpoint("mock:bar");
        mockEndpoint.expectedMinimumMessageCount(1);
        mockEndpoint.assertIsSatisfied(200);

        // Stop route and make sure all exchanges have been flushed.
        context.getRouteController().stopRoute("bar1");
        mockEndpoint.expectedMessageCount(3);
        mockEndpoint.assertIsSatisfied();

        resetMocks();
        template.sendBody("disruptor:foo", "D");
        template.sendBody("disruptor:foo", "E");
        template.sendBody("disruptor:foo", "F");

        // Add a new consumer on the endpoint
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("disruptor:foo").routeId("bar2").to("mock:bar");
            }
        });
        template.sendBody("disruptor:foo", "G");

        // Make sure we have received the 3 buffered exchanges plus the one
        // added late.
        mockEndpoint = getMockEndpoint("mock:bar");
        mockEndpoint.expectedMessageCount(4);
        mockEndpoint.assertIsSatisfied(100);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo").to("disruptor:foo?size=8");
            }
        };

    }
}
