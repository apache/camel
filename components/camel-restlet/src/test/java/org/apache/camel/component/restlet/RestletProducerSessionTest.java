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
package org.apache.camel.component.restlet;

import javax.servlet.http.HttpSession;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpMessage;
import org.apache.camel.http.common.cookie.ExchangeCookieHandler;
import org.apache.camel.http.common.cookie.InstanceCookieHandler;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class RestletProducerSessionTest extends RestletTestSupport {
    private String url = "restlet:http://127.0.0.1:" + portNum + "/session?restletMethod=POST";

    @Test
    public void testProducerNoSession() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("{New New World}", "{New New World}");
        template.sendBodyAndHeader("direct:start", "{World}", Exchange.CONTENT_TYPE, "application/json");
        template.sendBodyAndHeader("direct:start", "{World}", Exchange.CONTENT_TYPE, "application/json");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testProducerInstanceSession() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("{Old New World}", "{Old Old World}");
        template.sendBodyAndHeader("direct:instance", "{World}", Exchange.CONTENT_TYPE, "application/json");
        template.sendBodyAndHeader("direct:instance", "{World}", Exchange.CONTENT_TYPE, "application/json");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testProducerExchangeSession() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("{Old New World}", "{Old New World}");
        template.sendBodyAndHeader("direct:exchange", "{World}", Exchange.CONTENT_TYPE, "application/json");
        template.sendBodyAndHeader("direct:exchange", "{World}", Exchange.CONTENT_TYPE, "application/json");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndiRegistry = super.createRegistry();
        jndiRegistry.bind("instanceCookieHandler", new InstanceCookieHandler());
        jndiRegistry.bind("exchangeCookieHandler", new ExchangeCookieHandler());
        return jndiRegistry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to(url)
                    .to(url)
                    .to("mock:result");

                from("direct:instance")
                    .to(url + "&cookieHandler=#instanceCookieHandler")
                    .to(url + "&cookieHandler=#instanceCookieHandler")
                    .to("mock:result");

                from("direct:exchange")
                    .to(url + "&cookieHandler=#exchangeCookieHandler")
                    .to(url + "&cookieHandler=#exchangeCookieHandler")
                    .to("mock:result");

                from("jetty://http://127.0.0.1:" + portNum + "/session?sessionSupport=true")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            HttpMessage message = exchange.getIn(HttpMessage.class);
                            HttpSession session = message.getRequest().getSession();
                            String body = message.getBody(String.class);
                            if (body.length() > 2) {
                                body = body.substring(1, body.length() - 1);
                            }
                            if ("bar".equals(session.getAttribute("foo"))) {
                                body = "{Old " + body + "}";
                            } else {
                                session.setAttribute("foo", "bar");
                                body = "{New " + body + "}";
                            }
                            exchange.getOut().setBody(body);
                            exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/json");
                        }
                    });
            }
        };
    }
}
