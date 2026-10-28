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
package org.apache.camel.component.cometd;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.LocalSession;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A producer and a consumer share the CometD server of their host and port: the server is only stopped with the last of
 * them, so what a consumer registers on the server must be removed when it stops, and what the component registers must
 * be registered once.
 */
public class CometdConsumerRestartTest extends CamelTestSupport {

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    private final CountingListener listener = new CountingListener();
    private String uri;

    @Test
    void testRestartedConsumerReceivesAMessageOnce() throws Exception {
        context.getRouteController().stopRoute("consumer");
        context.getRouteController().startRoute("consumer");

        MockEndpoint mock = getMockEndpoint("mock:test");
        mock.expectedBodiesReceived("Hello");

        template.sendBody("direct:input", "Hello");

        mock.assertIsSatisfied();
        // the message is delivered synchronously: the consumer of before the restart must not receive it too
        assertEquals(1, mock.getReceivedCounter());
    }

    @Test
    void testStoppedConsumerDoesNotReceive() throws Exception {
        context.getRouteController().stopRoute("consumer");

        template.sendBody("direct:input", "Hello");

        assertEquals(0, getMockEndpoint("mock:test").getReceivedCounter());
    }

    @Test
    void testServerListenerIsCalledOncePerSession() {
        // the producer and the consumer are both connected to the server
        CometdConsumer consumer = (CometdConsumer) context.getRoute("consumer").getConsumer();
        LocalSession session = consumer.getConsumerService().getBayeux().newLocalSession("probe");
        listener.added.set(0);
        session.handshake();
        try {
            assertEquals(1, listener.added.get());
        } finally {
            session.disconnect();
        }
    }

    @Override
    public void doPreSetup() {
        uri = "cometd://127.0.0.1:" + port.getPort() + "/service/test?baseResource=file:./target/test-classes/webapp&"
              + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getComponent("cometd", CometdComponent.class).addServerListener(listener);

                from("direct:input").to(uri);

                from(uri).routeId("consumer").to("mock:test");
            }
        };
    }

    public static final class CountingListener implements BayeuxServer.SessionListener {

        private final AtomicInteger added = new AtomicInteger();

        @Override
        public void sessionAdded(ServerSession session, ServerMessage message) {
            added.incrementAndGet();
        }
    }
}
