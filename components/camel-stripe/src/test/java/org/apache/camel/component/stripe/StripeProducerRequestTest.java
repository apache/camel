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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Subscription;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Tests the requests the producer sends, against a local HTTP server as the Stripe API.
 */
public class StripeProducerRequestTest {

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> requestBodies = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private CamelContext context;
    private ProducerTemplate template;
    private String apiBase;

    @BeforeEach
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.add(exchange.getRequestMethod() + " " + path);
            try (InputStream is = exchange.getRequestBody()) {
                requestBodies.add(URLDecoder.decode(new String(is.readAllBytes(), StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8));
            }
            byte[] bytes = responseFor(path).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        apiBase = "http://localhost:" + server.getAddress().getPort();

        context = new DefaultCamelContext();
        context.start();
        template = context.createProducerTemplate();
    }

    @AfterEach
    public void tearDown() {
        if (context != null) {
            context.stop();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    private static String responseFor(String path) {
        String[] parts = path.split("/");
        String id = parts.length > 3 ? parts[3] : "cus_new";
        String object = switch (parts[2]) {
            case "charges" -> "charge";
            case "payment_intents" -> "payment_intent";
            case "subscriptions" -> "subscription";
            default -> "customer";
        };
        return "{\"id\":\"" + id + "\",\"object\":\"" + object + "\"}";
    }

    private String uri(String operation) {
        return "stripe:" + operation + "?apiKey=sk_test_123&apiBase=" + apiBase;
    }

    @Test
    public void testCreateFromJsonBytes() {
        byte[] body = "{\"name\":\"Jane Doe\",\"email\":\"jane@example.com\"}".getBytes(StandardCharsets.UTF_8);

        Object result = template.requestBody(uri("customers"), body);

        assertInstanceOf(Customer.class, result);
        assertEquals(List.of("POST /v1/customers"), requests);
        assertEquals("name=Jane Doe&email=jane@example.com", requestBodies.get(0));
    }

    @Test
    public void testCreateFromJsonStream() {
        InputStream body = new ByteArrayInputStream(
                "{\"name\":\"Jane Doe\",\"email\":\"jane@example.com\"}".getBytes(StandardCharsets.UTF_8));

        Object result = template.requestBody(uri("customers"), body);

        assertInstanceOf(Customer.class, result);
        assertEquals(List.of("POST /v1/customers"), requests);
        assertEquals("name=Jane Doe&email=jane@example.com", requestBodies.get(0));
    }

    @Test
    public void testOperationHeaderOverridesEndpoint() {
        Object result = template.requestBodyAndHeaders(uri("charges"), null,
                Map.of(StripeConstants.OPERATION_HEADER, "customers",
                        StripeConstants.METHOD_HEADER, StripeConstants.METHOD_RETRIEVE,
                        StripeConstants.OBJECT_ID, "cus_123"));

        assertInstanceOf(Customer.class, result);
        assertEquals(List.of("GET /v1/customers/cus_123"), requests);
    }

    @Test
    public void testOperationHeaderAsEnum() {
        Object result = template.requestBodyAndHeaders(uri("charges"), null,
                Map.of(StripeConstants.OPERATION_HEADER, StripeOperation.PAYMENT_INTENTS,
                        StripeConstants.METHOD_HEADER, StripeConstants.METHOD_RETRIEVE,
                        StripeConstants.OBJECT_ID, "pi_123"));

        assertInstanceOf(PaymentIntent.class, result);
        assertEquals(List.of("GET /v1/payment_intents/pi_123"), requests);
    }

    @Test
    public void testUpdateSendsOneRequest() {
        Object result = template.requestBodyAndHeaders(uri("customers"), Map.of("name", "John Doe"),
                Map.of(StripeConstants.METHOD_HEADER, StripeConstants.METHOD_UPDATE,
                        StripeConstants.OBJECT_ID, "cus_123"));

        assertInstanceOf(Customer.class, result);
        assertEquals("cus_123", ((Customer) result).getId());
        assertEquals(List.of("POST /v1/customers/cus_123"), requests);
        assertEquals("name=John Doe", requestBodies.get(0));
    }

    @Test
    public void testDeleteSendsOneRequest() {
        Object result = template.requestBodyAndHeaders(uri("customers"), null,
                Map.of(StripeConstants.METHOD_HEADER, StripeConstants.METHOD_DELETE,
                        StripeConstants.OBJECT_ID, "cus_123"));

        assertInstanceOf(Customer.class, result);
        assertEquals(List.of("DELETE /v1/customers/cus_123"), requests);
    }

    @Test
    public void testCaptureSendsOneRequest() {
        Object result = template.requestBodyAndHeaders(uri("charges"), null,
                Map.of(StripeConstants.METHOD_HEADER, StripeConstants.METHOD_CAPTURE,
                        StripeConstants.OBJECT_ID, "ch_123"));

        assertInstanceOf(Charge.class, result);
        assertEquals(List.of("POST /v1/charges/ch_123/capture"), requests);
    }

    @Test
    public void testCancelSendsOneRequest() {
        Object result = template.requestBodyAndHeaders(uri("paymentIntents"), null,
                Map.of(StripeConstants.METHOD_HEADER, StripeConstants.METHOD_CANCEL,
                        StripeConstants.OBJECT_ID, "pi_123"));
        assertInstanceOf(PaymentIntent.class, result);

        result = template.requestBodyAndHeaders(uri("subscriptions"), null,
                Map.of(StripeConstants.METHOD_HEADER, StripeConstants.METHOD_CANCEL,
                        StripeConstants.OBJECT_ID, "sub_123"));
        assertInstanceOf(Subscription.class, result);

        assertEquals(List.of("POST /v1/payment_intents/pi_123/cancel", "DELETE /v1/subscriptions/sub_123"), requests);
    }
}
