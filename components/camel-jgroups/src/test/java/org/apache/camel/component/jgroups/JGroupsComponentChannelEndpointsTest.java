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
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.jgroups.JChannel;
import org.jgroups.ObjectMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The channel of the component is used by every endpoint of the component: an endpoint whose producer or consumer
 * stops, or that is stopped itself, must not disconnect or close the channel while the other endpoints use it. The
 * channel and a channel given to an endpoint belong to the user: the CamelContext disconnects them when it stops, but
 * does not close them, so they can be used again when it starts again.
 */
class JGroupsComponentChannelEndpointsTest extends CamelTestSupport {

    private static final String CLUSTER = "componentChannelEndpoints";

    private JChannel componentChannel;
    private JChannel endpointChannel;
    private JChannel sender;

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();
        componentChannel = new JChannel();
        sender = new JChannel();
        sender.connect(CLUSTER);
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        // a channel given to one endpoint (channel=#endpointChannel) is not created by the endpoint either
        endpointChannel = new JChannel();
        registry.bind("endpointChannel", endpointChannel);
    }

    @Override
    public void doPostTearDown() {
        sender.close();
        componentChannel.close();
        endpointChannel.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                JGroupsComponent component = new JGroupsComponent();
                component.setChannel(componentChannel);
                context.addComponent("jgroups", component);

                // two endpoints of the same cluster: the consumer has an option the producer has not
                from("direct:send").routeId("producer").to("jgroups:" + CLUSTER);
                from("jgroups:" + CLUSTER + "?acceptAllObjects=true").routeId("consumer").to("mock:received");

                from("jgroups:" + CLUSTER + "B?channel=#endpointChannel&acceptAllObjects=true").to("mock:endpointChannel");
            }
        };
    }

    @Test
    void stoppingTheProducerKeepsTheConsumerReceiving() throws Exception {
        context.getRouteController().stopRoute("producer");

        MockEndpoint mock = getMockEndpoint("mock:received");
        mock.expectedBodiesReceived("after the producer stopped");

        sender.send(new ObjectMessage(null, "after the producer stopped"));

        mock.assertIsSatisfied();
        assertTrue(componentChannel.isConnected());
    }

    @Test
    void removingTheProducerRouteKeepsTheConsumerReceiving() throws Exception {
        context.getRouteController().stopRoute("producer");
        context.removeRoute("producer");

        MockEndpoint mock = getMockEndpoint("mock:received");
        mock.expectedBodiesReceived("after the producer route was removed");

        sender.send(new ObjectMessage(null, "after the producer route was removed"));

        mock.assertIsSatisfied();
        assertTrue(componentChannel.isConnected());
    }

    @Test
    void aConsumerThatFailsToStartKeepsTheChannelOfTheProducerConnected() throws Exception {
        context.getRouteController().stopRoute("consumer");
        assertTrue(componentChannel.isConnected(), "connected for the producer");

        // no acceptAllObjects and no filter: the consumer refuses to start, and is stopped
        addRouteWithAConsumerThatFailsToStart();

        assertTrue(componentChannel.isConnected(), "the channel of the producer is still connected");
    }

    @Test
    void aConsumerThatFailsToStartKeepsTheOtherConsumerReceiving() throws Exception {
        addRouteWithAConsumerThatFailsToStart();

        MockEndpoint mock = getMockEndpoint("mock:received");
        mock.expectedBodiesReceived("after a consumer failed to start");

        sender.send(new ObjectMessage(null, "after a consumer failed to start"));

        mock.assertIsSatisfied();
    }

    private void addRouteWithAConsumerThatFailsToStart() {
        assertThrows(Exception.class, () -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jgroups:" + CLUSTER + "?enableViewMessages=true").routeId("failing").to("mock:failing");
            }
        }));
    }

    @Test
    void stoppingTheCamelContextDisconnectsTheChannelsButDoesNotCloseThem() {
        assertTrue(componentChannel.isConnected());
        assertTrue(endpointChannel.isConnected());

        context.stop();

        assertFalse(componentChannel.isConnected());
        assertFalse(endpointChannel.isConnected());
        assertFalse(componentChannel.isClosed());
        assertFalse(endpointChannel.isClosed());
    }

    @Test
    void stoppingTheCamelContextDisconnectsTheChannelThatAProducerNotStoppedByCamelStillUses() throws Exception {
        // a producer that the application created and started itself: Camel does not stop it
        JGroupsEndpoint endpoint = context.getEndpoint("jgroups:" + CLUSTER, JGroupsEndpoint.class);
        Producer producer = endpoint.createProducer();
        producer.start();

        context.stop();

        assertFalse(componentChannel.isConnected(), "the node leaves the cluster when Camel stops");
        assertFalse(componentChannel.isClosed());
        // the late stop of that producer finds the channel disconnected
        assertDoesNotThrow(producer::stop);
        assertFalse(componentChannel.isClosed());
    }

    @Test
    void theRoutesReceiveAgainAfterTheCamelContextIsStoppedAndStartedAgain() throws Exception {
        context.stop();
        // the CamelContext of the test drops the component added in the route builder and the beans of its registry
        // when it stops: configure them again with the same channels, as an application that keeps them has them
        JGroupsComponent component = new JGroupsComponent();
        component.setChannel(componentChannel);
        context.addComponent("jgroups", component);
        context.getRegistry().bind("endpointChannel", endpointChannel);
        context.start();
        assertTrue(componentChannel.isConnected());
        assertTrue(endpointChannel.isConnected());

        MockEndpoint mock = getMockEndpoint("mock:received");
        mock.expectedBodiesReceived("after a restart");
        MockEndpoint endpointChannelMock = getMockEndpoint("mock:endpointChannel");
        endpointChannelMock.expectedBodiesReceived("after a restart, endpoint channel");

        sender.send(new ObjectMessage(null, "after a restart"));
        try (JChannel senderB = new JChannel()) {
            senderB.connect(CLUSTER + "B");
            senderB.send(new ObjectMessage(null, "after a restart, endpoint channel"));
            MockEndpoint.assertIsSatisfied(context);
        }
    }
}
