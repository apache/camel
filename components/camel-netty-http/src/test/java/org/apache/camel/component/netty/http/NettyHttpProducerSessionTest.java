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
package org.apache.camel.component.netty.http;

import jakarta.servlet.http.HttpSession;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.base.cookie.ExchangeCookieHandler;
import org.apache.camel.http.base.cookie.InstanceCookieHandler;
import org.apache.camel.http.common.HttpMessage;
import org.junit.jupiter.api.Test;

public class NettyHttpProducerSessionTest extends BaseNettyTestSupport {

    @BindToRegistry("instanceCookieHandler")
    private InstanceCookieHandler instanceCookieHandler = new InstanceCookieHandler();

    @BindToRegistry("exchangeCookieHandler")
    private ExchangeCookieHandler exchangeCookieHandler = new ExchangeCookieHandler();

    @Test
    public void testNoSession() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("New New World", "New New World");
        template.sendBody("direct:start", "World");
        template.sendBody("direct:start", "World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInstanceSession() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Old New World", "Old Old World");
        template.sendBody("direct:instance", "World");
        template.sendBody("direct:instance", "World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testExchangeSession() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Old New World", "Old New World");
        template.sendBody("direct:exchange", "World");
        template.sendBody("direct:exchange", "World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").toF("netty-http:http://127.0.0.1:%d/session", getPort())
                        .toF("netty-http:http://127.0.0.1:%d/session", getPort()).to("mock:result");

                from("direct:instance")
                        .toF("netty-http:http://127.0.0.1:%d/session?cookieHandler=#instanceCookieHandler", getPort())
                        .toF("netty-http:http://127.0.0.1:%d/session?cookieHandler=#instanceCookieHandler", getPort())
                        .to("mock:result");

                from("direct:exchange")
                        .toF("netty-http:http://127.0.0.1:%d/session?cookieHandler=#exchangeCookieHandler", getPort())
                        .toF("netty-http:http://127.0.0.1:%d/session?cookieHandler=#exchangeCookieHandler", getPort())
                        .to("mock:result");

                fromF("jetty:http://127.0.0.1:%d/session?sessionSupport=true", getPort()).process(new Processor() {
                    @Override
                    public void process(Exchange exchange) {
                        HttpMessage message = exchange.getIn(HttpMessage.class);
                        HttpSession session = message.getRequest().getSession();
                        String body = message.getBody(String.class);
                        if ("bar".equals(session.getAttribute("foo"))) {
                            message.setBody("Old " + body);
                        } else {
                            session.setAttribute("foo", "bar");
                            message.setBody("New " + body);
                        }
                    }
                });
            }
        };
    }
}
