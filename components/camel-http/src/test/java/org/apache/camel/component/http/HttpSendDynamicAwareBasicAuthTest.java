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

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.DrinkAuthValidationHandler;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpSendDynamicAwareBasicAuthTest extends BaseHttpTest {

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/joes", new DrinkAuthValidationHandler(GET.name(), null, null, "drink")).create();

        localServer.start();

        super.setUp();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        Properties prop = new Properties();
        prop.put("myUsername", "scott");
        prop.put("myPassword", "tiger");
        context.getPropertiesComponent().setInitialProperties(prop);

        return context;
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
                // Providing the username and password as user info is considered as an HTTP protocol violation
                // according to the RFC 7230, so its support has been removed
                //                from("direct:moes")
                //                        .toD("http://{{myUsername}}:{{myPassword}}@localhost:" + localServer.getLocalPort()
                //                             + "/moes?authMethod=Basic&authenticationPreemptive=true&throwExceptionOnFailure=false&drink=${header.drink}");

                from("direct:joes")
                        .toD("http://localhost:" + localServer.getLocalPort()
                             + "/joes?authMethod=Basic&authUsername={{myUsername}}&authPassword={{myPassword}}&authenticationPreemptive=true&throwExceptionOnFailure=false&drink=${header.drink}");
            }
        };
    }

    @Test
    public void testDynamicAware() throws Exception {
        String out = fluentTemplate.to("direct:joes").withHeader("drink", "wine").request(String.class);
        assertEquals("Drinking wine", out);

        // and there should be one http endpoint
        long count = context.getEndpoints().stream().filter(e -> e instanceof HttpEndpoint).count();
        assertEquals(1, count);

        // we only have one direct and one http
        assertEquals(2, context.getEndpointRegistry().size());
    }

}
