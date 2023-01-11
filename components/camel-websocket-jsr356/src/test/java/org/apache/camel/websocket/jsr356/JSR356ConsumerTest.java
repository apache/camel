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
package org.apache.camel.websocket.jsr356;

import java.io.IOException;
import java.net.URI;

import jakarta.enterprise.context.Dependent;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.server.ServerEndpoint;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit5.MeecrowaveConfig;
import org.apache.meecrowave.testing.ConfigurationInject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

@MeecrowaveConfig(scanningPackageIncludes = "org.apache.camel.websocket.jsr356.JSR356ConsumerTest$")
public class JSR356ConsumerTest extends CamelTestSupport {

    @ConfigurationInject
    protected Meecrowave.Builder configuration;

    protected String testMethodName;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        super.beforeEach(context);
        testMethodName = context.getRequiredTestMethod().getName();
    }

    @Test
    public void ensureClientModeReceiveProperlyExchanges() throws Exception {
        final String message = ExistingServerEndpoint.class.getName() + "#" + testMethodName;
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:" + testMethodName);
        mockEndpoint.expectedBodiesReceived(message);
        ExistingServerEndpoint.doSend(); // to avoid lifecycle issue during
                                        // startup we send the message
                                        // only here
        mockEndpoint.assertIsSatisfied();
        // note that this test leaks a connection
    }

    @Test
    public void ensureServerModeReceiveProperlyExchanges() throws Exception {
        final String message = getClass().getName() + "#" + testMethodName;
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:" + testMethodName);
        mockEndpoint.expectedBodiesReceived(message);

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                // no-op
            }
        }, ClientEndpointConfig.Builder.create().build(),
                URI.create("ws://localhost:" + configuration.getHttpPort() + "/test"));
        session.getBasicRemote().sendText(message);
        session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "bye"));

        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("websocket-jsr356:///test?sessionCount=5").id("camel_consumer_acts_as_server").convertBodyTo(String.class)
                        .to("mock:ensureServerModeReceiveProperlyExchanges");

                from("websocket-jsr356://ws://localhost:" + configuration.getHttpPort() + "/existingserver?sessionCount=5")
                        .id("camel_consumer_acts_as_client")
                        .convertBodyTo(String.class).to("mock:ensureClientModeReceiveProperlyExchanges");
            }
        };
    }

    @Dependent
    @ServerEndpoint("/existingserver")
    public static class ExistingServerEndpoint {
        private static Session session;

        @OnOpen
        public void onOpen(final Session session) {
            this.session = session;
        }

        static void doSend() throws IOException {
            session.getBasicRemote()
                    .sendText(ExistingServerEndpoint.class.getName() + "#ensureClientModeReceiveProperlyExchanges");
        }
    }
}
