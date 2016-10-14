/**
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
package org.apache.camel.component.undertow;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.cookie.ExchangeCookieHandler;
import org.apache.camel.http.common.cookie.InstanceCookieHandler;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;

public class UndertowHttpProducerSessionTest extends CamelTestSupport {
    private static volatile int port;

    @BeforeClass
    public static void initPort() throws Exception {
        port = AvailablePortFinder.getNextAvailable(24000);
    }

    @Test
    public void testNoSession() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("New New World", "New New World");
        template.sendBody("direct:start", "World");
        template.sendBody("direct:start", "World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInstanceSession() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Old New World", "Old Old World");
        template.sendBody("direct:instance", "World");
        template.sendBody("direct:instance", "World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExchangeSession() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Old New World", "Old New World");
        template.sendBody("direct:exchange", "World");
        template.sendBody("direct:exchange", "World");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndiRegistry = super.createRegistry();
        jndiRegistry.bind("instanceCookieHandler", new InstanceCookieHandler());
        jndiRegistry.bind("exchangeCookieHandler", new ExchangeCookieHandler());
        return jndiRegistry;
    }

    private String getTestServerEndpointSessionUrl() {
        // session handling will not work for localhost
        return "http://127.0.0.1:" + port + "/session";
    }

    private String getTestServerEndpointSessionUri() {
        return "undertow:" + getTestServerEndpointSessionUrl() + "?sessionSupport=true";
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("undertow:" + getTestServerEndpointSessionUrl())
                    .to("undertow:" + getTestServerEndpointSessionUrl())
                    .to("mock:result");

                from("direct:instance")
                    .to("undertow:" + getTestServerEndpointSessionUrl() + "?cookieHandler=#instanceCookieHandler")
                    .to("undertow:" + getTestServerEndpointSessionUrl() + "?cookieHandler=#instanceCookieHandler")
                    .to("mock:result");

                from("direct:exchange")
                    .to("undertow:" + getTestServerEndpointSessionUrl() + "?cookieHandler=#exchangeCookieHandler")
                    .to("undertow:" + getTestServerEndpointSessionUrl() + "?cookieHandler=#exchangeCookieHandler")
                    .to("mock:result");

                from(getTestServerEndpointSessionUri())
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            Message message = exchange.getIn();
                            String body = message.getBody(String.class);
                            // Undertow servers do not support sessions or
                            // cookies, so we fake them
                            if (message.getHeader("Cookie") != null && message.getHeader("Cookie", String.class).contains("JSESSIONID")) {
                                message.setBody("Old " + body);
                            } else {
                                message.setHeader("Set-Cookie", "JSESSIONID=nxojb3aum8i5100j6lyvxdpn6;Path=/");
                                message.setHeader("Expires", "Thu, 01 Jan 1970 00:00:00 GMT");
                                message.setBody("New " + body);
                            }
                        }
                    });
            }
        };
    }
}
