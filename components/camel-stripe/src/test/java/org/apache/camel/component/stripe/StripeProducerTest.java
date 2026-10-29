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
package org.apache.camel.component.stripe;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.stripe.Stripe;
import com.stripe.net.BearerTokenAuthenticator;
import com.stripe.net.RequestOptions;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StripeProducerTest {

    private HttpServer server;

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void testProducerBuildsEndpointRequestOptions() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            StripeComponent component = new StripeComponent();
            component.setCamelContext(context);
            StripeConfiguration config = new StripeConfiguration();
            config.setOperation(StripeOperation.CHARGES);
            config.setApiKey("sk_test_custom_key_1");
            config.setApiBase("http://localhost:8999");

            StripeEndpoint endpoint = new StripeEndpoint("stripe:charges", component, config);
            StripeProducer producer = new StripeProducer(endpoint);

            producer.start();
            try {
                RequestOptions options = producer.getRequestOptions();
                assertNotNull(options);
                assertInstanceOf(BearerTokenAuthenticator.class, options.getAuthenticator());
                assertEquals("sk_test_custom_key_1", ((BearerTokenAuthenticator) options.getAuthenticator()).getApiKey());
                assertEquals("http://localhost:8999", options.getBaseUrl());
                assertNotEquals("sk_test_custom_key_1", Stripe.apiKey);
                assertEquals(Stripe.LIVE_API_BASE, Stripe.getApiBase());
            } finally {
                producer.stop();
            }
        }
    }

    @Test
    public void testMultipleProducersIsolateOptions() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            StripeComponent component = new StripeComponent();
            component.setCamelContext(context);

            StripeConfiguration config1 = new StripeConfiguration();
            config1.setOperation(StripeOperation.CHARGES);
            config1.setApiKey("sk_test_account_1");
            config1.setApiBase("http://localhost:8001");
            StripeEndpoint endpoint1 = new StripeEndpoint("stripe:charges?apiKey=sk_test_account_1", component, config1);
            StripeProducer producer1 = new StripeProducer(endpoint1);

            StripeConfiguration config2 = new StripeConfiguration();
            config2.setOperation(StripeOperation.CUSTOMERS);
            config2.setApiKey("sk_test_account_2");
            config2.setApiBase("http://localhost:8002");
            StripeEndpoint endpoint2 = new StripeEndpoint("stripe:customers?apiKey=sk_test_account_2", component, config2);
            StripeProducer producer2 = new StripeProducer(endpoint2);

            producer1.start();
            producer2.start();
            try {
                assertEquals("sk_test_account_1",
                        ((BearerTokenAuthenticator) producer1.getRequestOptions().getAuthenticator()).getApiKey());
                assertEquals("http://localhost:8001", producer1.getRequestOptions().getBaseUrl());

                assertEquals("sk_test_account_2",
                        ((BearerTokenAuthenticator) producer2.getRequestOptions().getAuthenticator()).getApiKey());
                assertEquals("http://localhost:8002", producer2.getRequestOptions().getBaseUrl());
            } finally {
                producer1.stop();
                producer2.stop();
            }
        }
    }

    @Test
    public void testRequestOptionsIsolationInHttpRequests() throws Exception {
        List<String> authHeaders = new ArrayList<>();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/charges/ch_test_123", exchange -> {
            authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            String response = "{\"id\":\"ch_test_123\",\"object\":\"charge\",\"amount\":1000}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        String localBase = "http://localhost:" + server.getAddress().getPort();

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            StripeComponent component = new StripeComponent();
            component.setCamelContext(context);

            StripeConfiguration config1 = new StripeConfiguration();
            config1.setOperation(StripeOperation.CHARGES);
            config1.setApiKey("sk_test_first_account");
            config1.setApiBase(localBase);
            StripeEndpoint endpoint1 = new StripeEndpoint("stripe:charges", component, config1);
            StripeProducer producer1 = new StripeProducer(endpoint1);

            StripeConfiguration config2 = new StripeConfiguration();
            config2.setOperation(StripeOperation.CHARGES);
            config2.setApiKey("sk_test_second_account");
            config2.setApiBase(localBase);
            StripeEndpoint endpoint2 = new StripeEndpoint("stripe:charges", component, config2);
            StripeProducer producer2 = new StripeProducer(endpoint2);

            // Start producer 1, then start producer 2 afterwards
            producer1.start();
            producer2.start();

            try {
                // Request through producer1
                Exchange exchange1 = endpoint1.createExchange();
                exchange1.getIn().setHeader(StripeConstants.METHOD_HEADER, StripeConstants.METHOD_RETRIEVE);
                exchange1.getIn().setHeader(StripeConstants.OBJECT_ID, "ch_test_123");
                producer1.process(exchange1);

                // Request through producer2
                Exchange exchange2 = endpoint2.createExchange();
                exchange2.getIn().setHeader(StripeConstants.METHOD_HEADER, StripeConstants.METHOD_RETRIEVE);
                exchange2.getIn().setHeader(StripeConstants.OBJECT_ID, "ch_test_123");
                producer2.process(exchange2);

                assertEquals(2, authHeaders.size());
                assertEquals("Bearer sk_test_first_account", authHeaders.get(0));
                assertEquals("Bearer sk_test_second_account", authHeaders.get(1));
            } finally {
                producer1.stop();
                producer2.stop();
            }
        }
    }

    @Test
    public void testMissingApiKeyFailsStartup() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            StripeComponent component = new StripeComponent();
            component.setCamelContext(context);
            StripeConfiguration config = new StripeConfiguration();
            config.setOperation(StripeOperation.CHARGES);

            StripeEndpoint endpoint = new StripeEndpoint("stripe:charges", component, config);
            StripeProducer producer = new StripeProducer(endpoint);

            assertThrows(IllegalArgumentException.class, () -> producer.start());
        }
    }
}
