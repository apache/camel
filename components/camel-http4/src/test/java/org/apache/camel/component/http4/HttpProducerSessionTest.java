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
package org.apache.camel.component.http4;

import java.net.InetSocketAddress;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.handler.SessionReflectionHandler;
import org.apache.camel.http.common.cookie.ExchangeCookieHandler;
import org.apache.camel.http.common.cookie.InstanceCookieHandler;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class HttpProducerSessionTest extends CamelTestSupport {
    private static volatile int port;
    private static Server localServer;

    @BeforeClass
    public static void initServer() throws Exception {
        port = AvailablePortFinder.getNextAvailable(24000);
        localServer = new Server(new InetSocketAddress("127.0.0.1", port));
        ContextHandler contextHandler = new ContextHandler();
        contextHandler.setContextPath("/session");
        SessionHandler sessionHandler = new SessionHandler();
        sessionHandler.setHandler(new SessionReflectionHandler());
        contextHandler.setHandler(sessionHandler);
        localServer.setHandler(contextHandler);
        localServer.start();
    }

    @AfterClass
    public static void shutdownServer() throws Exception {
        localServer.stop();
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
        jndiRegistry.bind("noopCookieStore", new NoopCookieStore());
        return jndiRegistry;
    }

    private String getTestServerEndpointSessionUrl() {
        // session handling will not work for localhost
        return "http4://127.0.0.1:" + port + "/session/";
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
