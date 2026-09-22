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
package org.apache.camel.component.openai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The webhook consumer over HTTP: platform-http serves the endpoint the consumer registers, and the requests are the
 * ones OpenAI sends, signed the way the Standard Webhooks specification describes.
 */
class OpenAIWebhookHttpTest {

    private static final String SECRET
            = "whsec_" + Base64.getEncoder().encodeToString("a-webhook-secret".getBytes(StandardCharsets.UTF_8));

    private static final String EVENT
            = "{\"id\":\"evt_http\",\"created_at\":1758000000,\"type\":\"response.completed\","
              + "\"data\":{\"id\":\"resp_http\"},\"object\":\"event\"}";

    private int port;
    private CamelContext context;
    private HttpClient http;

    @BeforeEach
    void startRoute() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        http = HttpClient.newHttpClient();

        VertxPlatformHttpServerConfiguration configuration = new VertxPlatformHttpServerConfiguration();
        configuration.setBindPort(port);
        context = new DefaultCamelContext();
        context.getShutdownStrategy().setTimeout(5);
        context.addService(new VertxPlatformHttpServer(configuration));
        // nothing creates the platform-http component here: the consumer resolves it from the classpath
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("openai:webhook?webhookSecret=" + SECRET + "&webhookPath=/openai/events")
                        .to("mock:events");
            }
        });
        context.start();
    }

    @AfterEach
    void stopRoute() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void aSignedEventIsAcceptedAndRunsTheRoute() throws Exception {
        MockEndpoint events = context.getEndpoint("mock:events", MockEndpoint.class);
        events.expectedMessageCount(1);
        events.expectedHeaderReceived(OpenAIConstants.WEBHOOK_OBJECT_ID, "resp_http");

        HttpResponse<String> response = post(EVENT, now());

        assertThat(response.statusCode()).isEqualTo(200);
        events.assertIsSatisfied(TimeUnit.SECONDS.toMillis(10));
    }

    @Test
    void aForgedEventIsAnsweredWith400() throws Exception {
        MockEndpoint events = context.getEndpoint("mock:events", MockEndpoint.class);
        events.expectedMessageCount(0);

        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/openai/events"))
                        .header("Content-Type", "application/json")
                        .header("webhook-id", "evt_http")
                        .header("webhook-timestamp", String.valueOf(now()))
                        .header("webhook-signature", "v1,Zm9yZ2Vk")
                        .POST(HttpRequest.BodyPublishers.ofString(EVENT))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(400);
        events.assertIsSatisfied(TimeUnit.SECONDS.toMillis(1));
    }

    private HttpResponse<String> post(String payload, long timestamp) throws Exception {
        String id = "evt_http";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET.substring("whsec_".length())), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(
                mac.doFinal((id + "." + timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));

        return http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/openai/events"))
                        .header("Content-Type", "application/json")
                        .header("webhook-id", id)
                        .header("webhook-timestamp", String.valueOf(timestamp))
                        .header("webhook-signature", "v1," + signature)
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }
}
