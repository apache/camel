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
package org.apache.camel.component.jgroups;

import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.jgroups.JChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer and the consumer of an endpoint share the channel the endpoint created: removing the consumer route must
 * not close the channel that the producer route still sends with.
 */
class JGroupsRemoveRouteTest extends CamelTestSupport {

    private static final String SHARED = "jgroups:removeRouteCluster?acceptAllObjects=true";
    // another endpoint (its own channel) in the same cluster, which receives what the producer sends;
    // enableViewMessages=false only makes its URI differ from SHARED, so that it is another endpoint instance
    private static final String OTHER = "jgroups:removeRouteCluster?acceptAllObjects=true&enableViewMessages=false";

    @Test
    void removeConsumerRoute() throws Exception {
        MockEndpoint other = getMockEndpoint("mock:other");
        other.expectedBodiesReceived("a");
        template.sendBody("direct:send", "a");
        other.assertIsSatisfied();

        context.getRouteController().stopRoute("consumer");
        context.removeRoute("consumer");

        other.reset();
        other.expectedBodiesReceived("b");
        template.sendBody("direct:send", "b");
        other.assertIsSatisfied();
    }

    @Test
    void removeConsumerRouteThenAddItAgain() throws Exception {
        JGroupsEndpoint endpoint = context.getEndpoint(SHARED, JGroupsEndpoint.class);
        JChannel channel = endpoint.getResolvedChannel();

        context.getRouteController().stopRoute("consumer");
        context.removeRoute("consumer");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(SHARED).routeId("consumer2").to("mock:consumer2");
            }
        });

        MockEndpoint other = getMockEndpoint("mock:other");
        other.expectedBodiesReceived("c");
        // the re-added consumer receives on the kept channel (a channel also receives what it sends itself)
        MockEndpoint consumer2 = getMockEndpoint("mock:consumer2");
        consumer2.expectedBodiesReceived("c");
        template.sendBody("direct:send", "c");
        MockEndpoint.assertIsSatisfied(other, consumer2);
        // the restarted endpoint keeps the channel the producer is still connected to: a new channel would leave the
        // old one connected to the cluster after both routes stop
        assertSame(channel, endpoint.getResolvedChannel(), "the re-added consumer uses the channel of the producer");
    }

    @Test
    void removeBothRoutesClosesTheChannel() throws Exception {
        JChannel channel = context.getEndpoint(SHARED, JGroupsEndpoint.class).getResolvedChannel();

        context.getRouteController().stopRoute("consumer");
        context.removeRoute("consumer");
        assertTrue(channel.isConnected(), "the producer still uses the channel");

        context.getRouteController().stopRoute("producer");
        context.removeRoute("producer");
        assertTrue(channel.isClosed(), "the last user of the channel closed it");
    }

    @Test
    void stopCamelClosesTheChannelThatAProducerNotStoppedByCamelStillUses() throws Exception {
        JGroupsEndpoint endpoint = context.getEndpoint(SHARED, JGroupsEndpoint.class);
        JChannel channel = endpoint.getResolvedChannel();
        // a producer that the application created and started itself: Camel does not stop it
        Producer producer = endpoint.createProducer();
        producer.start();

        context.stop();

        assertTrue(channel.isClosed(), "the channel is closed when Camel stops, as the node must leave the cluster");
        // the late stop of that producer finds the channel closed
        assertDoesNotThrow(producer::stop);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:send").routeId("producer").to(SHARED);
                from(SHARED).routeId("consumer").to("mock:consumer");

                from(OTHER).routeId("other").to("mock:other");
            }
        };
    }
}
