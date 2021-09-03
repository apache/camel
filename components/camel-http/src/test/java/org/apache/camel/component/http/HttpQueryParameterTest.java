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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.DrinkQueryValidationHandler;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpQueryParameterTest extends BaseHttpTest {

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setExpectationVerifier(getHttpExpectationVerifier()).setSslContext(getSSLContext())
                .registerHandler("/moes", new DrinkQueryValidationHandler(GET.name(), null, null, "drink"))
                .registerHandler("/joes", new DrinkQueryValidationHandler(GET.name(), null, null, "drink")).create();
        localServer.start();

        super.setUp();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:moes")
                        .to("http://localhost:" + localServer.getLocalPort()
                            + "/moes?drink=beer");

                from("direct:joes")
                        .to("http://localhost:" + localServer.getLocalPort()
                            + "/joes?drink=wine");
            }
        };
    }

    @Test
    public void testQueryParameter() throws Exception {
        String out = fluentTemplate.to("direct:moes").request(String.class);
        assertEquals("Drinking /moes?drink=beer", out);

        out = fluentTemplate.to("direct:joes").request(String.class);
        assertEquals("Drinking /joes?drink=wine", out);
    }

}
