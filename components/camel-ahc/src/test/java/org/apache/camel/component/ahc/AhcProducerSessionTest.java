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
package org.apache.camel.component.ahc;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpMessage;
import org.apache.camel.http.common.cookie.ExchangeCookieHandler;
import org.apache.camel.http.common.cookie.InstanceCookieHandler;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class AhcProducerSessionTest extends BaseAhcTest {

    @Test
    public void testProducerNoSession() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("New New World", "New New World");
        template.sendBody("direct:start", "World");
        template.sendBody("direct:start", "World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testProducerInstanceSession() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Old New World", "Old Old World");
        template.sendBody("direct:instance", "World");
        template.sendBody("direct:instance", "World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testProducerExchangeSession() throws Exception {
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
        return getProtocol() + "://127.0.0.1:" + getPort() + "/session";
    }

    private String getTestServerEndpointSessionUri() {
        return "jetty:" + getTestServerEndpointSessionUrl() + "?sessionSupport=true";
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("ahc:" + getTestServerEndpointSessionUrl())
                    .to("ahc:" + getTestServerEndpointSessionUrl())
                    .to("mock:result");

                from("direct:instance")
                    .to("ahc:" + getTestServerEndpointSessionUrl() + "?cookieHandler=#instanceCookieHandler")
                    .to("ahc:" + getTestServerEndpointSessionUrl() + "?cookieHandler=#instanceCookieHandler")
                    .to("mock:result");

                from("direct:exchange")
                    .to("ahc:" + getTestServerEndpointSessionUrl() + "?cookieHandler=#exchangeCookieHandler")
                    .to("ahc:" + getTestServerEndpointSessionUrl() + "?cookieHandler=#exchangeCookieHandler")
                    .to("mock:result");

                from(getTestServerEndpointSessionUri())
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            HttpMessage message = exchange.getIn(HttpMessage.class);
                            HttpSession session = message.getRequest().getSession();
                            String body = message.getBody(String.class);
                            if ("bar".equals(session.getAttribute("foo"))) {
                                message.setBody("Old " + body);
                            } else {
                                session.setAttribute("foo", "bar");
                                message.setBody("New " + body);
                            }
                            message.getResponse().addCookie(new Cookie("othercookie", "value"));
                        }
                    });
            }
        };
    }
}
