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
package org.apache.camel.component.opensearch;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpensearchEndpointClientTest {

    private static final String BASIC_AUTH = "Basic dXNlcjpwYXNz";
    private static final String BEARER_AUTH = "Bearer test-token";

    private final AtomicInteger firstRequests = new AtomicInteger();
    private final AtomicInteger secondRequests = new AtomicInteger();
    private HttpServer firstServer;
    private HttpServer secondServer;

    @BeforeEach
    void startServers() throws IOException {
        firstServer = startServer(BASIC_AUTH, firstRequests);
        secondServer = startServer(BEARER_AUTH, secondRequests);
    }

    @AfterEach
    void stopServers() {
        firstServer.stop(0);
        secondServer.stop(0);
    }

    @Test
    void endpointClientOverridesComponentClient() throws Exception {
        try (RestClient firstRestClient = createClient(firstServer, BASIC_AUTH);
             RestClient secondRestClient = createClient(secondServer, BEARER_AUTH);
             CamelContext context = new DefaultCamelContext()) {
            OpenSearchClient firstClient = openSearchClient(firstRestClient);
            OpenSearchClient secondClient = openSearchClient(secondRestClient);
            context.getRegistry().bind("firstClient", firstClient);
            context.getRegistry().bind("secondClient", secondClient);
            OpensearchComponent component = new OpensearchComponent();
            component.setOpenSearchClient(firstClient);
            context.addComponent("opensearch", component);

            String firstUri = "opensearch:first?operation=Ping&openSearchClient=#bean:firstClient";
            String secondUri = "opensearch:second?operation=Ping&openSearchClient=#bean:secondClient";
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:both").to(firstUri).to(secondUri);
                    from("direct:default").to("opensearch:default?operation=Ping");
                }
            });
            context.start();
            try (ProducerTemplate template = context.createProducerTemplate()) {
                assertSame(firstClient, context.getEndpoint(firstUri, OpensearchEndpoint.class).getOpenSearchClient());
                assertSame(secondClient, context.getEndpoint(secondUri, OpensearchEndpoint.class).getOpenSearchClient());
                assertEquals(Boolean.TRUE, template.requestBody("direct:both", "", Boolean.class));
                assertEquals(1, firstRequests.get());
                assertEquals(1, secondRequests.get());

                assertEquals(Boolean.TRUE, template.requestBody("direct:default", "", Boolean.class));
                assertEquals(2, firstRequests.get());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void camelCloseNotTheClientOnShutDown(boolean disconnect) throws Exception {
        try (RestClient restClient = createClient(firstServer, BASIC_AUTH);
             CamelContext context = new DefaultCamelContext()) {
            OpenSearchClient client = openSearchClient(restClient);
            context.getRegistry().bind("customClient", client);
            OpensearchComponent component = new OpensearchComponent();
            component.setClient(restClient);
            context.addComponent("opensearch", component);
            String uri = "opensearch:custom?operation=Ping&openSearchClient=#bean:customClient&disconnect=" + disconnect;
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:ping").to(uri);
                }
            });
            context.start();
            assertTrue(restClient.isRunning());

            context.stop();
            assertTrue(restClient.isRunning());
        }
    }

    @Test
    void componentClientIsStillAutowiredWithoutEndpointOverride() throws Exception {
        try (RestClient restClient = createClient(firstServer, BASIC_AUTH);
             CamelContext context = new DefaultCamelContext()) {
            OpenSearchClient client = openSearchClient(restClient);
            context.getRegistry().bind("customClient", client);
            context.start();
            OpensearchEndpoint endpoint = context.getEndpoint("opensearch:default", OpensearchEndpoint.class);

            assertSame(client, context.getComponent("opensearch", OpensearchComponent.class).getOpenSearchClient());
            assertSame(client, endpoint.getOpenSearchClient());
        }
    }

    private static HttpServer startServer(String authorization, AtomicInteger requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            boolean authorized = authorization.equals(exchange.getRequestHeaders().getFirst("Authorization"));
            if (authorized) {
                requests.incrementAndGet();
            }
            exchange.sendResponseHeaders(authorized ? 200 : 401, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static RestClient createClient(HttpServer server, String authorization) {
        return RestClient.builder(new HttpHost("http", "127.0.0.1", server.getAddress().getPort()))
                .setDefaultHeaders(new BasicHeader[] { new BasicHeader("Authorization", authorization) })
                .build();
    }

    private static OpenSearchClient openSearchClient(RestClient client) {
        return new OpenSearchClient(new RestClientTransport(client, new JacksonJsonpMapper()));
    }
}
