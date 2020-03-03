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
package org.apache.camel.component.ahc;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.cookie.ExchangeCookieHandler;
import org.apache.camel.http.base.cookie.InstanceCookieHandler;
import org.apache.camel.http.common.HttpMessage;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.junit.jupiter.api.Test;

public class AhcProducerSessionTest extends BaseAhcTest {

    @BindToRegistry("instanceCookieHandler")
    InstanceCookieHandler instanceCookieHandler = new InstanceCookieHandler();
   
    @BindToRegistry("exchangeCookieHandler")
    ExchangeCookieHandler exchangeCookieHandler = new ExchangeCookieHandler();

    @BindToRegistry("noCookieConfig")
    AsyncHttpClientConfig noCookieConfig = (new DefaultAsyncHttpClientConfig.Builder()).setCookieStore(null).build();

    @BindToRegistry("defaultConfig")
    AsyncHttpClientConfig defaultConfig = (new DefaultAsyncHttpClientConfig.Builder()).build();

    @Test
    public void testProducerNoSession() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("New New World", "New New World");
        template.sendBody("direct:start", "World");
        template.sendBody("direct:start", "World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testProducerNoSessionWithConfig() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("New New World", "New New World");
        template.sendBody("direct:config", "World");
        template.sendBody("direct:config", "World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testProducerSessionFromAhcClient() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Old New World", "Old Old World");
        template.sendBody("direct:defaultconfig", "World");
        template.sendBody("direct:defaultconfig", "World");
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

                from("direct:config")
                    .to("ahc:" + getTestServerEndpointSessionUrl() + "?clientConfig=#noCookieConfig")
                    .to("ahc:" + getTestServerEndpointSessionUrl() + "?clientConfig=#noCookieConfig")
                    .to("mock:result");

                from("direct:defaultconfig")
                    .to("ahc:" + getTestServerEndpointSessionUrl() + "?clientConfig=#defaultConfig")
                    .to("ahc:" + getTestServerEndpointSessionUrl() + "?clientConfig=#defaultConfig")
                    .to("mock:result");

                from("direct:instance")
                    .to("ahc:" + getTestServerEndpointSessionUrl() + "?cookieHandler=#instanceCookieHandler")
                    .to("ahc:" + getTestServerEndpointSessionUrl() + "?cookieHandler=#instanceCookieHandler")
                    .to("mock:result");

                from("direct:exchange")
                    .to("ahc:" + getTestServerEndpointSessionUrl() + "?clientConfig=#noCookieConfig&cookieHandler=#exchangeCookieHandler")
                    .to("ahc:" + getTestServerEndpointSessionUrl() + "?clientConfig=#noCookieConfig&cookieHandler=#exchangeCookieHandler")
                    .to("mock:result");

                from(getTestServerEndpointSessionUri())
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            HttpMessage message = exchange.getIn(HttpMessage.class);
                            Object cookiesObj = message.getHeader("Cookie");
                            HttpSession session = message.getRequest().getSession();
                            String body = message.getBody(String.class);
                            if ("bar".equals(session.getAttribute("foo"))) {
                                message.setBody("Old " + body);
                                /*
                                 * If we are in a session we should also have a cookie header with two
                                 * cookies. This test checks that the cookies are in one line.
                                 * We can also get the cookies with request.getCookies() but this will
                                 * always give us two cookies even if there are two cookie headers instead
                                 * of one multi-value cookie header.
                                 */
                                if (cookiesObj instanceof String && ((String) cookiesObj).contains("othercookie=value")) {
                                    if (!((String) cookiesObj).contains("JSESSIONID=")) {
                                        log.error("JSESSIONID missing");
                                        throw new IllegalStateException("JSESSIONID missing");
                                    }
                                } else {
                                    log.error("othercookie=value is missing in cookie");
                                    throw new IllegalStateException("othercookie=value is missing in cookie");
                                }
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
