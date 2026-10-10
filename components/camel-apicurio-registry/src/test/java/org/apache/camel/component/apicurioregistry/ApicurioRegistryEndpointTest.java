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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
    void testEndpointsShareComponentVertxAndCloseTheirWebClients() throws Exception {
        Vertx vertx = mock(Vertx.class);
        when(vertx.close()).thenReturn(Future.succeededFuture());
        List<Vertx> clientVertx = new ArrayList<>();
        List<WebClient> clientWebClients = new ArrayList<>();
        List<WebClient> created = new ArrayList<>();
        try (MockedStatic<Vertx> vertxFactory = mockStatic(Vertx.class);
             MockedStatic<WebClient> webClientFactory = mockStatic(WebClient.class);
             MockedStatic<RegistryClientFactory> clientFactory = mockStatic(RegistryClientFactory.class);
             DefaultCamelContext context = new DefaultCamelContext()) {
            vertxFactory.when(Vertx::vertx).thenReturn(vertx);
            webClientFactory.when(() -> WebClient.create(any(Vertx.class), any(WebClientOptions.class))).thenAnswer(call -> {
                assertThat(call.getArgument(0, Vertx.class)).isSameAs(vertx);
                WebClient webClient = mock(WebClient.class);
                created.add(webClient);
                return webClient;
            });
            clientFactory.when(() -> RegistryClientFactory.create(any())).thenAnswer(call -> {
                RegistryClientOptions options = call.getArgument(0, RegistryClientOptions.class);
                clientVertx.add(options.getVertx());
                clientWebClients.add(options.getWebClient());
                return mock(RegistryClient.class);
            });
            context.start();
            ApicurioRegistryEndpoint first = context.getEndpoint(
                    "apicurio-registry:g/a?registryUrl=http://localhost:8080/apis/registry/v3", ApicurioRegistryEndpoint.class);
            ApicurioRegistryEndpoint second = context.getEndpoint(
                    "apicurio-registry:g/b?registryUrl=http://localhost:8080/apis/registry/v3", ApicurioRegistryEndpoint.class);
            first.start();
            second.start();
            assertThat(clientVertx).containsExactly(vertx, vertx);
            assertThat(clientWebClients).containsExactlyElementsOf(created);
            vertxFactory.verify(Vertx::vertx, times(1));

            // stopping an endpoint closes its WebClient, but keeps the shared Vert.x instance
            first.stop();
            assertThat(first.getRegistryClient()).isNull();
            verify(created.get(0)).close();
            verify(created.get(1), never()).close();
            first.start();
            assertThat(first.getRegistryClient()).isNotNull();
            assertThat(created).hasSize(3);
            verify(vertx, never()).close();
            vertxFactory.verify(Vertx::vertx, times(1));

            second.stop();
            first.stop();
            created.forEach(webClient -> verify(webClient).close());
            context.getComponent("apicurio-registry").stop();
            verify(vertx).close();
        }
    }

    @Test
    void testProvidedVertxIsNotClosed() throws Exception {
        Vertx vertx = mock(Vertx.class);
        try (MockedStatic<Vertx> vertxFactory = mockStatic(Vertx.class);
             MockedStatic<WebClient> webClientFactory = mockStatic(WebClient.class);
             MockedStatic<RegistryClientFactory> clientFactory = mockStatic(RegistryClientFactory.class);
             DefaultCamelContext context = new DefaultCamelContext()) {
            webClientFactory.when(() -> WebClient.create(any(Vertx.class), any(WebClientOptions.class)))
                    .thenAnswer(call -> mock(WebClient.class));
            clientFactory.when(() -> RegistryClientFactory.create(any())).thenAnswer(call -> {
                assertThat(call.getArgument(0, RegistryClientOptions.class).getVertx()).isSameAs(vertx);
                return mock(RegistryClient.class);
            });
            context.getComponent("apicurio-registry", ApicurioRegistryComponent.class).setVertx(vertx);
            context.start();
            ApicurioRegistryEndpoint endpoint = context.getEndpoint(
                    "apicurio-registry:g/a?registryUrl=http://localhost:8080/apis/registry/v3", ApicurioRegistryEndpoint.class);
            endpoint.start();
            context.stop();
            vertxFactory.verify(Vertx::vertx, never());
            verify(vertx, never()).close();
        }
    }

    @Test
    void testVertxSetAfterComponentCreatedOneIsNotClosed() throws Exception {
        Vertx managed = mock(Vertx.class);
        when(managed.close()).thenReturn(Future.succeededFuture());
        Vertx provided = mock(Vertx.class);
        try (MockedStatic<Vertx> vertxFactory = mockStatic(Vertx.class);
             DefaultCamelContext context = new DefaultCamelContext()) {
            vertxFactory.when(Vertx::vertx).thenReturn(managed);
            ApicurioRegistryComponent component = context.getComponent("apicurio-registry", ApicurioRegistryComponent.class);
            context.start();
            assertThat(component.getOrCreateVertx()).isSameAs(managed);

            component.setVertx(provided);
            assertThat(component.getOrCreateVertx()).isSameAs(provided);
            component.stop();
            verify(provided, never()).close();
            // the instance the component created itself is still closed
            verify(managed).close();
        }
    }

    @Test
    void testTlsAndProxyOptions() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            SSLContextParameters ssl = new SSLContextParameters();
            KeyStoreParameters trustStore = new KeyStoreParameters();
            trustStore.setResource("classpath:truststore.p12");
            trustStore.setType("PKCS12");
            trustStore.setPassword("changeit");
            TrustManagersParameters trustManagers = new TrustManagersParameters();
            trustManagers.setKeyStore(trustStore);
            ssl.setTrustManagers(trustManagers);
            context.getRegistry().bind("ssl", ssl);
            ApicurioRegistryEndpoint endpoint = context.getEndpoint(
                    "apicurio-registry:g/a?registryUrl=https://registry:8443/apis/registry/v3&sslContextParameters=#ssl"
                                                                    + "&proxyHost=proxy&proxyPort=3128&proxyUsername=user&proxyPassword=pass",
                    ApicurioRegistryEndpoint.class);

            WebClientOptions options = endpoint.createWebClientOptions();
            assertThat(options.isSsl()).isTrue();
            assertThat(options.getTrustOptions()).isNotNull();
            assertThat(options.getProxyOptions().getHost()).isEqualTo("proxy");
            assertThat(options.getProxyOptions().getPort()).isEqualTo(3128);
            assertThat(options.getProxyOptions().getUsername()).isEqualTo("user");
            assertThat(options.getProxyOptions().getPassword()).isEqualTo("pass");
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
