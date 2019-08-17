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

import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.SecurityPolicy;
import org.cometd.bayeux.server.ServerChannel;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit testing for using a CometdProducer and a CometdConsumer
 */
public class CometdProducerConsumerTest extends CamelTestSupport {

    private static final String SHOOKHANDS_SESSION_HEADER = "Shookhands";
    private int port;
    private String uri;

    @Test
    public void testProducer() throws Exception {
        Person person = new Person("David", "Greco");
        //act
        template.requestBodyAndHeader("direct:input", person, "testHeading", "value");

        //assert
        MockEndpoint ep = context.getEndpoint("mock:test", MockEndpoint.class);
        List<Exchange> exchanges = ep.getReceivedExchanges();
        for (Exchange exchange : exchanges) {
            Message message = exchange.getIn();
            Person person1 = (Person) message.getBody();
            assertEquals("David", person1.getName());
            assertEquals("Greco", person1.getSurname());
        }
    }

    @Test
    public void testHeadersSupported() throws Exception {
        //setup
        String headerName = "testHeading";
        String headerValue = "value";

        //act
        template.requestBodyAndHeader("direct:input", "message", headerName, headerValue);

        //assert
        MockEndpoint ep = context.getEndpoint("mock:test", MockEndpoint.class);
        List<Exchange> exchanges = ep.getReceivedExchanges();
        assertTrue(exchanges.size() > 0);
        for (Exchange exchange : exchanges) {
            Message message = exchange.getIn();
            assertEquals(headerValue, message.getHeader(headerName));
            assertNotNull(message.getHeader(CometdBinding.COMETD_CLIENT_ID_HEADER_NAME));
        }
    }
    
    @Test
    public void testSessionHeaderArgumentSet() throws Exception {
        // setup
        CometdComponent component = context.getComponent("cometd", CometdComponent.class);

        // act
        Endpoint result = component
            .createEndpoint("cometd://127.0.0.1:"
                            + port
                            + "/service/testArgs?baseResource=file:./target/test-classes/webapp&"
                            + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&sessionHeadersEnabled=true&logLevel=2");

        // assert
        assertTrue(result instanceof CometdEndpoint);
        CometdEndpoint cometdEndpoint = (CometdEndpoint)result;
        assertTrue(cometdEndpoint.isSessionHeadersEnabled());
    }
    
    @Test
    public void testSessionInformationTransferred() throws Exception {
        // act
        template.sendBody("direct:input", "message");

        // assert
        MockEndpoint ep = context.getEndpoint("mock:test", MockEndpoint.class);
        List<Exchange> exchanges = ep.getReceivedExchanges();
        assertTrue(exchanges.size() > 0);
        for (Exchange exchange : exchanges) {
            Message message = exchange.getIn();
            assertTrue((Boolean)message.getHeader(SHOOKHANDS_SESSION_HEADER));
        }
    }


    @Override
    @Before
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        uri = "cometd://127.0.0.1:" + port + "/service/test?baseResource=file:./target/test-classes/webapp&"
                + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&sessionHeadersEnabled=true&logLevel=2";

        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                CometdComponent component = context.getComponent("cometd", CometdComponent.class);
                // The security policy is used to set session attributes.
                component.setSecurityPolicy(createTestSecurityPolicy());
                from("direct:input").to(uri);
                from(uri).to("mock:test");
            }
        };

    }

    private SecurityPolicy createTestSecurityPolicy() {
        return new SecurityPolicy() {

            @Override
            public boolean canSubscribe(BayeuxServer server, ServerSession session, ServerChannel channel,
                                        ServerMessage message) {
                session.setAttribute("Subscribed", true);
                return true;
            }

            @Override
            public boolean canPublish(BayeuxServer server, ServerSession session, ServerChannel channel,
                                      ServerMessage message) {
                return true;
            }

            @Override
            public boolean canHandshake(BayeuxServer server, ServerSession session, ServerMessage message) {
                session.setAttribute(SHOOKHANDS_SESSION_HEADER, true);
                return true;
            }

            @Override
            public boolean canCreate(BayeuxServer server, ServerSession session, String channelId,
                                     ServerMessage message) {
                return true;
            }
        };
    }

    public static class Person {

        private String name;
        private String surname;

        Person(String name, String surname) {
            this.name = name;
            this.surname = surname;
        }

        public String getName() {
            return name;
        }

        public String getSurname() {
            return surname;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setSurname(String surname) {
            this.surname = surname;
        }
    }
}

