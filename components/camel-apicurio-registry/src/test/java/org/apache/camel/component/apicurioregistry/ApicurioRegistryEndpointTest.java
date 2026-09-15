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
package org.apache.camel.component.apicurioregistry;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApicurioRegistryEndpointTest {

    @Test
    void testBasicAuthentication() throws Exception {
        testAuthentication("basic&username=user&password=pass", "Basic dXNlcjpwYXNz", false);
    }

    @Test
    void testOidcAuthentication() throws Exception {
        testAuthentication("oidc&clientId=registry-client&clientSecret=secret&scope=registry", "Bearer test-token", true);
    }

    private void testAuthentication(String options, String authorization, boolean oidc) throws Exception {
        AtomicReference<String> registryAuthorization = new AtomicReference<>();
        AtomicReference<String> tokenRequest = new AtomicReference<>();
        AtomicReference<String> tokenAuthorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/token", request -> {
            tokenRequest.set(new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            tokenAuthorization.set(request.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                    .getBytes(StandardCharsets.UTF_8);
            request.getResponseHeaders().set("Content-Type", "application/json");
            request.sendResponseHeaders(200, response.length);
            try (var body = request.getResponseBody()) {
                body.write(response);
            }
        });
        server.createContext("/apis/registry/v3/groups/g/artifacts/a", request -> {
            registryAuthorization.set(request.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"artifactId\":\"a\",\"groupId\":\"g\"}".getBytes(StandardCharsets.UTF_8);
            request.getResponseHeaders().set("Content-Type", "application/json");
            request.sendResponseHeaders(200, response.length);
            try (var body = request.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            ApicurioRegistryEndpoint endpoint = context.getEndpoint(
                    "apicurio-registry:g/a?registryUrl=" + baseUrl + "/apis/registry/v3&authType=" + options
                                                                    + (oidc ? "&tokenEndpoint=" + baseUrl + "/token" : ""),
                    ApicurioRegistryEndpoint.class);
            endpoint.start();
            try {
                assertThat(endpoint.getRegistryClient().groups().byGroupId("g").artifacts().byArtifactId("a")
                        .get().getArtifactId()).isEqualTo("a");
                assertThat(registryAuthorization.get()).isEqualTo(authorization);
                if (oidc) {
                    assertThat(tokenRequest.get()).contains("grant_type=client_credentials", "scope=registry");
                    assertThat(tokenAuthorization.get()).isEqualTo("Basic cmVnaXN0cnktY2xpZW50OnNlY3JldA==");
                }
            } finally {
                endpoint.stop();
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testOwnedResourcesClosedOnStopAndRestart() throws Exception {
        Vertx vertx = mock(Vertx.class);
        when(vertx.close()).thenReturn(Future.succeededFuture());
        RegistryClient client = mock(RegistryClient.class);
        try (MockedStatic<Vertx> vertxFactory = mockStatic(Vertx.class);
             MockedStatic<RegistryClientFactory> clientFactory = mockStatic(RegistryClientFactory.class);
             DefaultCamelContext context = new DefaultCamelContext()) {
            vertxFactory.when(Vertx::vertx).thenReturn(vertx);
            clientFactory.when(() -> RegistryClientFactory.create(any())).thenAnswer(call -> {
                assertThat(call.getArgument(0, RegistryClientOptions.class).getVertx())
                        .isSameAs(vertx);
                return client;
            });
            ApicurioRegistryEndpoint endpoint = context.getEndpoint(
                    "apicurio-registry:g/a?registryUrl=http://localhost:8080/apis/registry/v3", ApicurioRegistryEndpoint.class);
            endpoint.start();
            assertThat(endpoint.getRegistryClient()).isSameAs(client);
            endpoint.stop();
            assertThat(endpoint.getRegistryClient()).isNull();
            endpoint.start();
            endpoint.stop();
            verify(vertx, times(2)).close();
        }
    }

    @Test
    void testFailedClientCreationClosesResources() throws Exception {
        Vertx vertx = mock(Vertx.class);
        when(vertx.close()).thenReturn(Future.succeededFuture());
        try (MockedStatic<Vertx> vertxFactory = mockStatic(Vertx.class);
             MockedStatic<RegistryClientFactory> clientFactory = mockStatic(RegistryClientFactory.class);
             DefaultCamelContext context = new DefaultCamelContext()) {
            vertxFactory.when(Vertx::vertx).thenReturn(vertx);
            clientFactory.when(() -> RegistryClientFactory.create(any()))
                    .thenThrow(new IllegalArgumentException("invalid client"));
            ApicurioRegistryEndpoint endpoint = context.getEndpoint(
                    "apicurio-registry:g/a?registryUrl=http://localhost:8080/apis/registry/v3", ApicurioRegistryEndpoint.class);
            assertThatThrownBy(endpoint::start).isInstanceOf(IllegalArgumentException.class).hasMessage("invalid client");
            verify(vertx).close();
        }
    }

    @Test
    void testCallerOwnedClientSurvivesRestart() throws Exception {
        RegistryClient client = mock(RegistryClient.class);
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            ApicurioRegistryEndpoint endpoint = context.getEndpoint(
                    "apicurio-registry:g/a?registryUrl=http://localhost:8080/apis/registry/v3", ApicurioRegistryEndpoint.class);
            endpoint.setRegistryClient(client);
            endpoint.start();
            endpoint.stop();
            endpoint.start();
            assertThat(endpoint.getRegistryClient()).isSameAs(client);
            endpoint.stop();
        }
    }
}
