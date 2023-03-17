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
package org.apache.camel.component.http;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.SessionReflectionHandler;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.base.cookie.ExchangeCookieHandler;
import org.apache.camel.http.base.cookie.InstanceCookieHandler;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.jetty.services.JettyConfiguration;
import org.apache.camel.test.infra.jetty.services.JettyConfigurationBuilder;
import org.apache.camel.test.infra.jetty.services.JettyEmbeddedService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class HttpProducerSessionTest extends CamelTestSupport {
    private static final int PORT = AvailablePortFinder.getNextAvailable();

    private final JettyConfiguration jettyConfiguration = JettyConfigurationBuilder
            .emptyTemplate()
            .withPort(PORT)
            .withContextPath("/session")
            .withContextHandlerConfiguration().withCustomizer(HttpProducerSessionTest::customizer)
            .build().build();

    @RegisterExtension
    public JettyEmbeddedService service = new JettyEmbeddedService(jettyConfiguration);

    @BindToRegistry("instanceCookieHandler")
    private InstanceCookieHandler instanceHandler = new InstanceCookieHandler();

    @BindToRegistry("exchangeCookieHandler")
    private ExchangeCookieHandler exchangeHandler = new ExchangeCookieHandler();

    @BindToRegistry("noopCookieStore")
    private NoopCookieStore cookieStore = new NoopCookieStore();

    private static void customizer(ContextHandler contextHandler) {
        SessionHandler sessionHandler = new SessionHandler();
        sessionHandler.setHandler(new SessionReflectionHandler());
        contextHandler.setHandler(sessionHandler);
    }

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

    private String getTestServerEndpointSessionUrl() {
        // session handling will not work for localhost
        return "http://localhost:" + PORT + "/session/";
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to(getTestServerEndpointSessionUrl() + "?cookieStore=#noopCookieStore")
                        .to(getTestServerEndpointSessionUrl() + "?cookieStore=#noopCookieStore")
                        .to("mock:result");

                from("direct:instance")
                        .to(getTestServerEndpointSessionUrl() + "?cookieHandler=#instanceCookieHandler")
                        .to(getTestServerEndpointSessionUrl() + "?cookieHandler=#instanceCookieHandler")
                        .to("mock:result");

                from("direct:exchange")
                        .to(getTestServerEndpointSessionUrl() + "?cookieHandler=#exchangeCookieHandler")
                        .to(getTestServerEndpointSessionUrl() + "?cookieHandler=#exchangeCookieHandler")
                        .to("mock:result");
            }
        };
    }
}
