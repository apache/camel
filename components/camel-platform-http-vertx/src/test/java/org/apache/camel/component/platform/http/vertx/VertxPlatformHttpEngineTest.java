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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class VertxPlatformHttpEngineTest {
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
    public void testEngine() throws Exception {
        final int port = AvailablePortFinder.getNextAvailable();
        final CamelContext context = new DefaultCamelContext();

        try {
            VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
            conf.setBindPort(port);

            context.addService(new VertxPlatformHttpServer(context, conf), true, true);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("platform-http:/get")
                        .routeId("get")
                        .setBody().constant("get");
                    from("platform-http:/post")
                        .routeId("post")
                        .transform().body(String.class, b -> b.toUpperCase());
                }
            });

            context.start();

            assertThat(VertxPlatformHttp.lookup(context)).isNotNull();
            assertThat(context.getComponent("platform-http")).isInstanceOfSatisfying(PlatformHttpComponent.class, component -> {
                assertThat(component.getEngine()).isInstanceOfSatisfying(VertxPlatformHttpEngine.class, e -> {
                    assertThat(e.getRouter().router()).isNotNull();
                    assertThat(e.getRouter().handlers()).isNotEmpty();
                });
            });

            given()
                .port(conf.getBindPort())
            .when()
                .get("/get")
            .then()
                .statusCode(200)
                .body(equalTo("get"));

            given()
                .port(conf.getBindPort())
                .body("post")
            .when()
                .post("/post")
            .then()
                .statusCode(200)
                .body(equalTo("POST"));

        } finally {
            context.stop();
        }
    }

    @Test
    public void testEngineSSL() throws Exception {
        VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
        conf.setSslContextParameters(serverSSLParameters);
        conf.setBindPort(AvailablePortFinder.getNextAvailable());

        CamelContext context = new DefaultCamelContext();

        try {
            context.addService(new VertxPlatformHttpServer(context, conf), true, true);
            context.getRegistry().bind("clientSSLContextParameters", clientSSLParameters);

            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    fromF("platform-http:/")
                        .transform().body(String.class, b -> b.toUpperCase());
                }
            });

            context.start();

            String result = context.createFluentProducerTemplate()
                .toF("https://localhost:%d?sslContextParameters=#clientSSLContextParameters", conf.getBindPort())
                .withBody("test")
                .request(String.class);

            assertThat(result).isEqualTo("TEST");
        } finally {
            context.stop();
        }
    }

    @Test
    public void testEngineGlobalSSL() throws Exception {
        VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
        conf.setUseGlobalSslContextParameters(true);
        conf.setBindPort(AvailablePortFinder.getNextAvailable());

        CamelContext context = new DefaultCamelContext();

        try {
            context.setSSLContextParameters(serverSSLParameters);
            context.addService(new VertxPlatformHttpServer(context, conf), true, true);
            context.getRegistry().bind("clientSSLContextParameters", clientSSLParameters);

            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    fromF("platform-http:/")
                        .transform().body(String.class, b -> b.toUpperCase());
                }
            });

            context.start();

            String result = context.createFluentProducerTemplate()
                .toF("https://localhost:%d?sslContextParameters=#clientSSLContextParameters", conf.getBindPort())
                .withBody("test")
                .request(String.class);

            assertThat(result).isEqualTo("TEST");
        } finally {
            context.stop();
        }
    }
}
