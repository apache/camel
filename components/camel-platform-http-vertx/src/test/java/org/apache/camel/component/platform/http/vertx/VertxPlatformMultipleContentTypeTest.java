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
package org.apache.camel.component.platform.http.vertx;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;

public class VertxPlatformMultipleContentTypeTest {
    public static SSLContextParameters serverSSLParameters;
    public static SSLContextParameters clientSSLParameters;

    @BeforeAll
    public static void setUp() {
        serverSSLParameters = new SSLContextParameters();
        clientSSLParameters = new SSLContextParameters();

        KeyStoreParameters keystoreParameters = new KeyStoreParameters();
        keystoreParameters.setResource("jsse/service.jks");
        keystoreParameters.setPassword("security");

        KeyManagersParameters serviceSSLKeyManagers = new KeyManagersParameters();
        serviceSSLKeyManagers.setKeyPassword("security");
        serviceSSLKeyManagers.setKeyStore(keystoreParameters);

        serverSSLParameters.setKeyManagers(serviceSSLKeyManagers);

        KeyStoreParameters truststoreParameters = new KeyStoreParameters();
        truststoreParameters.setResource("jsse/truststore.jks");
        truststoreParameters.setPassword("storepass");

        TrustManagersParameters clientAuthServiceSSLTrustManagers = new TrustManagersParameters();
        clientAuthServiceSSLTrustManagers.setKeyStore(truststoreParameters);
        serverSSLParameters.setTrustManagers(clientAuthServiceSSLTrustManagers);
        SSLContextServerParameters clientAuthSSLContextServerParameters = new SSLContextServerParameters();
        clientAuthSSLContextServerParameters.setClientAuthentication("REQUIRE");
        serverSSLParameters.setServerParameters(clientAuthSSLContextServerParameters);

        TrustManagersParameters clientSSLTrustManagers = new TrustManagersParameters();
        clientSSLTrustManagers.setKeyStore(truststoreParameters);
        clientSSLParameters.setTrustManagers(clientSSLTrustManagers);

        KeyManagersParameters clientAuthClientSSLKeyManagers = new KeyManagersParameters();
        clientAuthClientSSLKeyManagers.setKeyPassword("security");
        clientAuthClientSSLKeyManagers.setKeyStore(keystoreParameters);
        clientSSLParameters.setKeyManagers(clientAuthClientSSLKeyManagers);
    }

    @Test
    public void testMultipleContentTypes() throws Exception {
        final CamelContext context = createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    restConfiguration().component("platform-http")
                            .contextPath("/rest");

                    rest().get("/test")
                            .consumes("application/json,application/xml")
                            .produces("application/json,application/xml")
                            .bindingMode(RestBindingMode.auto)
                            .to("direct:rest");

                    from("direct:rest")
                            .setBody(simple("Hello"));
                }
            });

            context.start();

            RestAssured.given()
                    .header("Content-Type", "application/json,application/xml")
                    .get("rest/test")
                    .then()
                    .statusCode(415);

        } finally {
            context.stop();
        }
    }

    @Test
    public void testOneContentType() throws Exception {
        final CamelContext context = createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    restConfiguration().component("platform-http")
                            .contextPath("/rest");

                    rest().get("/test")
                            .consumes("application/json,application/xml")
                            .produces("application/json,application/xml")
                            .bindingMode(RestBindingMode.auto)
                            .to("direct:rest");

                    from("direct:rest")
                            .setBody(simple("Hello"));
                }
            });

            context.start();

            RestAssured.given()
                    .header("Content-Type", "application/json")
                    .get("rest/test")
                    .then()
                    .statusCode(200)
                    .body(is("\"Hello\""));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testAcceptsOneContentType() throws Exception {
        final CamelContext context = createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    restConfiguration().component("platform-http")
                            .contextPath("/rest");

                    rest().get("/test")
                            .consumes("application/xml,application/json")
                            .produces("application/xml,application/json")
                            .bindingMode(RestBindingMode.auto)
                            .to("direct:rest");

                    from("direct:rest")
                            .setBody(simple("Hello"));
                }
            });

            context.start();

            RestAssured.given()
                    .header("Content-type", ContentType.XML)
                    .header("Accept", ContentType.XML)
                    .get("rest/test")
                    .then()
                    .statusCode(200)
                    .body(is("Hello"));
        } finally {
            context.stop();
        }
    }

    static CamelContext createCamelContext() throws Exception {
        return createCamelContext(null);
    }

    private static CamelContext createCamelContext(ServerConfigurationCustomizer customizer) throws Exception {
        int port = AvailablePortFinder.getNextAvailable();
        VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
        conf.setBindPort(port);

        RestAssured.port = port;

        if (customizer != null) {
            customizer.customize(conf);
        }

        CamelContext context = new DefaultCamelContext();
        context.addService(new VertxPlatformHttpServer(conf));
        return context;
    }

    interface ServerConfigurationCustomizer {
        void customize(VertxPlatformHttpServerConfiguration configuration);
    }
}
