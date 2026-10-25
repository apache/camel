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
package org.apache.camel.component.es;

import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client of the component is shared by all its endpoints and is not closed by a producer, and a producer that
 * created its own client creates a new one when its route is started again.
 */
class ElasticsearchProducerClientLifecycleTest {

    private HttpServer server;
    private String hostAddress;

    @BeforeEach
    void startServer() throws Exception {
        // answers the ping (HEAD /) as an Elasticsearch node does
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("X-Elastic-Product", "Elasticsearch");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        hostAddress = "127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void restartedRouteCreatesANewClient() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(pingRoutes("elasticsearch:test-cluster?operation=Ping&hostAddresses=" + hostAddress));
            context.start();
            ProducerTemplate template = context.createProducerTemplate();
            assertEquals(Boolean.TRUE, template.requestBody("direct:a", "", Boolean.class));

            context.getRouteController().stopRoute("a");
            context.getRouteController().startRoute("a");

            assertEquals(Boolean.TRUE, template.requestBody("direct:a", "", Boolean.class));
        }
    }

    @Test
    void stoppedProducerClosesTheClientItCreated() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            Endpoint endpoint
                    = context.getEndpoint("elasticsearch:test-cluster?operation=Ping&hostAddresses=" + hostAddress);
            ElasticsearchProducer producer = (ElasticsearchProducer) endpoint.createProducer();
            producer.start();
            assertEquals(Boolean.TRUE, ping(endpoint, producer));
            RestClient first = producer.getClient();

            producer.stop();
            assertFalse(first.isRunning(), "the client the producer created was not closed");

            producer.start();
            assertEquals(Boolean.TRUE, ping(endpoint, producer));
            assertNotSame(first, producer.getClient());
            producer.stop();
        }
    }

    @Test
    void stoppingARouteKeepsTheClientOfTheComponent() throws Exception {
        try (RestClient client = RestClient.builder(HttpHost.create("http://" + hostAddress)).build();
             CamelContext context = new DefaultCamelContext()) {
            // autowired on the component, so shared by its endpoints
            context.getRegistry().bind("esClient", client);
            context.addRoutes(pingRoutes("elasticsearch:test-cluster?operation=Ping"));
            context.start();
            ProducerTemplate template = context.createProducerTemplate();
            assertEquals(Boolean.TRUE, template.requestBody("direct:a", "", Boolean.class));

            context.getRouteController().stopRoute("a");

            assertEquals(Boolean.TRUE, template.requestBody("direct:b", "", Boolean.class));
            assertTrue(client.isRunning(), "the client of the component was closed by a producer");

            context.getRouteController().startRoute("a");
            assertEquals(Boolean.TRUE, template.requestBody("direct:a", "", Boolean.class));

            context.stop();
            assertTrue(client.isRunning(), "the client of the component was closed when Camel stopped");
        }
    }

    @Test
    void disconnectKeepsTheClientOfTheComponent() throws Exception {
        try (RestClient client = RestClient.builder(HttpHost.create("http://" + hostAddress)).build();
             CamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("esClient", client);
            context.addRoutes(pingRoutes("elasticsearch:test-cluster?operation=Ping&disconnect=true"));
            context.start();
            ProducerTemplate template = context.createProducerTemplate();

            assertEquals(Boolean.TRUE, template.requestBody("direct:a", "", Boolean.class));
            assertEquals(Boolean.TRUE, template.requestBody("direct:b", "", Boolean.class));
            assertTrue(client.isRunning(), "the client of the component was closed after a request");
        }
    }

    private static Object ping(Endpoint endpoint, ElasticsearchProducer producer) throws Exception {
        Exchange exchange = endpoint.createExchange();
        producer.process(exchange);
        if (exchange.getException() != null) {
            throw exchange.getException();
        }
        return exchange.getMessage().getBody();
    }

    private static RouteBuilder pingRoutes(String uri) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a").to(uri);
                from("direct:b").routeId("b").to(uri);
            }
        };
    }
}
