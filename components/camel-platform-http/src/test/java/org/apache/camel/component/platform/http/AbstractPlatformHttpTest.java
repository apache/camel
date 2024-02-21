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
package org.apache.camel.component.platform.http;

import io.restassured.RestAssured;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;

abstract class AbstractPlatformHttpTest {
    protected static int port;
    private static final Object LOCK = new Object();
    private static JettyServerTest server;
    private static CamelContext ctx;

    @BeforeEach
    public void init() throws Exception {
        synchronized (LOCK) {
            if (ctx == null) {
                ctx = new DefaultCamelContext();
                ctx.getRegistry().bind(PlatformHttpConstants.PLATFORM_HTTP_ENGINE_FACTORY, new JettyCustomPlatformHttpEngine());

                port = AvailablePortFinder.getNextAvailable();
                server = new JettyServerTest(port);

                ctx.getRegistry().bind(JettyServerTest.JETTY_SERVER_NAME, server);
                server.start();

                ctx.addRoutes(routes());
                ctx.start();
            }
            RestAssured.baseURI = "http://localhost:" + port;
        }
    }

    protected RouteBuilder routes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
            }
        };
    }

    protected CamelContext getContext() {
        return ctx;
    }

    @AfterAll
    public static void tearDown() throws Exception {
        synchronized (LOCK) {
            ctx.stop();
            server.stop();
            ctx = null;
        }
    }

}
