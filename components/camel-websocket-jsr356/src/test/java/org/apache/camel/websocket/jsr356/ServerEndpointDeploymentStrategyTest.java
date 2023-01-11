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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Encoder;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit5.MeecrowaveConfig;
import org.apache.meecrowave.testing.ConfigurationInject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("TODO: Does not work with some deployment error in embedded JEE server")
@MeecrowaveConfig(scanningPackageIncludes = "org.apache.camel.websocket.jsr356.ServerEndpointDeploymentStrategyTest$")
public class ServerEndpointDeploymentStrategyTest extends CamelTestSupport {

    @ConfigurationInject
    Meecrowave.Builder configuration;

    @Test
    public void customDeploymentStrategyTest() throws Exception {
        LinkedBlockingDeque<String> message = new LinkedBlockingDeque<>();
        Session session = ContainerProvider.getWebSocketContainer().connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig endpointConfig) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(String s) {
                        message.add(s);
                    }
                });
                session.getAsyncRemote().sendText("Camel");
            }
        }, ClientEndpointConfig.Builder.create().build(), getEndpointURI("/greeting"));

        try {
            assertEquals("Hello Camel", message.poll(5, TimeUnit.SECONDS));
        } finally {
            session.close();
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        JSR356WebSocketComponent component = new JSR356WebSocketComponent();

        List<Class<? extends Encoder>> encoders = new ArrayList<>();
        encoders.add(GreetingEncoder.class);

        // Configure the ServerEndpoint with a custom Encoder
        component.setServerEndpointDeploymentStrategy((container, configBuilder) -> {
            configBuilder.encoders(encoders);
            container.addEndpoint(configBuilder.build());
        });

        camelContext.addComponent("websocket-jsr356", component);
        return camelContext;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("websocket-jsr356:/greeting")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                Session session = exchange.getMessage().getHeader(JSR356Constants.SESSION, Session.class);
                                session.getAsyncRemote().sendObject(exchange.getMessage().getBody(String.class));
                            }
                        });
            }
        };
    }

    private URI getEndpointURI(String path) throws URISyntaxException {
        return new URI(String.format("ws://%s:%d/%s", configuration.getHost(), configuration.getHttpPort(), path));
    }

}
