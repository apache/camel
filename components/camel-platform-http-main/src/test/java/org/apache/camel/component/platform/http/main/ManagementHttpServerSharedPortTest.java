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
package org.apache.camel.component.platform.http.main;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.camel.CamelContext;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.Main;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The management server shares the HTTP server when both have the same port, and the HTTP server then serves the
 * management endpoints with its own host, path, SSL and authentication.
 */
class ManagementHttpServerSharedPortTest {

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    @RegisterExtension
    AvailablePortFinder.Port managementPort = AvailablePortFinder.find();

    private Main main;

    @AfterEach
    void stopMain() {
        if (main != null) {
            main.stop();
        }
    }

    @Test
    void sharedPortServesManagementEndpointsOnTheHttpServer() throws Exception {
        createMain().start();

        assertEquals(200, get("localhost", port.getPort(), "/observe/info", null).statusCode());
    }

    @Test
    void sharedPortAcceptsTheHostAndPathOfTheHttpServer() throws Exception {
        createMain("camel.server.host", "127.0.0.1", "camel.management.host", "127.0.0.1",
                "camel.server.path", "/app", "camel.management.path", "/app").start();

        assertEquals(200, get("127.0.0.1", port.getPort(), "/app/observe/info", null).statusCode());
    }

    @Test
    void sharedPortAppliesTheAuthenticationOfTheHttpServer() throws Exception {
        createMain("camel.server.authenticationEnabled", "true", "camel.server.authenticationPath", "/*",
                "camel.server.basicPropertiesFile", "camel-platform-http-vertx-auth.properties").start();

        assertEquals(401, get("localhost", port.getPort(), "/observe/info", null).statusCode());
        assertEquals(200, get("localhost", port.getPort(), "/observe/info", "camel:propertiesPass").statusCode());
    }

    @Test
    void sharedPortRejectsAnotherManagementHost() {
        createMain("camel.management.host", "127.0.0.1");

        String message = startFailure();
        assertTrue(message.contains("camel.management.host=127.0.0.1"), message);
        assertTrue(message.contains("camel.management.port"), message);
    }

    @Test
    void sharedPortRejectsAnotherManagementPath() {
        createMain("camel.management.path", "/management");

        String message = startFailure();
        assertTrue(message.contains("camel.management.path=/management"), message);
    }

    @Test
    void sharedPortRejectsManagementGlobalSsl() {
        createMain("camel.management.useGlobalSslContextParameters", "true");

        String message = startFailure();
        assertTrue(message.contains("camel.management.useGlobalSslContextParameters=true"), message);
    }

    @Test
    void sharedPortRejectsManagementAuthentication() {
        createMain("camel.management.authenticationEnabled", "true",
                "camel.management.basicPropertiesFile", "camel-platform-http-vertx-auth.properties");

        String message = startFailure();
        assertTrue(message.contains("camel.management.authenticationEnabled"), message);
    }

    @Test
    void sharedPortRejectsManagementSslContextParameters() throws Exception {
        // only set programmatically: camel-main has no option for it
        try (CamelContext context = new DefaultCamelContext()) {
            VertxPlatformHttpServerConfiguration configuration = new VertxPlatformHttpServerConfiguration();
            configuration.setBindPort(port.getPort());
            context.addService(new VertxPlatformHttpServer(configuration));

            ManagementHttpServer server = new ManagementHttpServer();
            server.setCamelContext(context);
            server.setPort(port.getPort());
            server.setSslContextParameters(new SSLContextParameters());

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, server::init);
            assertTrue(e.getMessage().contains("sslContextParameters (the HTTP server uses its own SSL configuration)"),
                    e.getMessage());
        }
    }

    @Test
    void ownPortAppliesTheManagementSettings() throws Exception {
        createMain("camel.management.port", Integer.toString(managementPort.getPort()),
                "camel.management.host", "127.0.0.1", "camel.management.path", "/management",
                "camel.management.authenticationEnabled", "true",
                "camel.management.basicPropertiesFile", "camel-platform-http-vertx-auth.properties").start();

        assertEquals(401, get("127.0.0.1", managementPort.getPort(), "/management/observe/info", null).statusCode());
        assertEquals(200,
                get("127.0.0.1", managementPort.getPort(), "/management/observe/info", "camel:propertiesPass")
                        .statusCode());
        assertEquals(404, get("localhost", port.getPort(), "/observe/info", null).statusCode());
    }

    private Main createMain(String... properties) {
        main = new Main();
        main.addOverrideProperty("camel.server.enabled", "true");
        main.addOverrideProperty("camel.server.port", Integer.toString(port.getPort()));
        main.addOverrideProperty("camel.management.enabled", "true");
        main.addOverrideProperty("camel.management.port", Integer.toString(port.getPort()));
        main.addOverrideProperty("camel.management.infoEnabled", "true");
        for (int i = 0; i < properties.length; i += 2) {
            main.addOverrideProperty(properties[i], properties[i + 1]);
        }
        return main;
    }

    private String startFailure() {
        Exception e = assertThrows(Exception.class, main::start);
        Throwable cause = e;
        while (cause != null && !(cause instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "Expected an IllegalArgumentException, got: " + e);
        return cause.getMessage();
    }

    private static HttpResponse<String> get(String host, int port, String path, String credentials) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder().uri(URI.create("http://" + host + ":" + port + path));
        if (credentials != null) {
            request.header("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
