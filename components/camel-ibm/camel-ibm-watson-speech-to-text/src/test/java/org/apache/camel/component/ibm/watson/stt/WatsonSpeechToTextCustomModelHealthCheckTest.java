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
package org.apache.camel.component.ibm.watson.stt;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.ibm.cloud.sdk.core.security.NoAuthAuthenticator;
import com.ibm.watson.speech_to_text.v1.SpeechToText;
import com.ibm.watson.speech_to_text.v1.model.LanguageModel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.impl.health.DefaultHealthCheckRegistry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The getCustomModel operation takes the customization ID from its own header, and the producer has a health check that
 * lists the models of the service. The service is played by a JDK HttpServer.
 */
class WatsonSpeechToTextCustomModelHealthCheckTest extends CamelTestSupport {

    // the literal name, so that the test also runs against a version without the constant
    private static final String CUSTOMIZATION_ID_HEADER = "CamelIBMWatsonSTTCustomizationId";

    private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
    private static volatile int modelsStatus = 200;
    private static HttpServer server;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", WatsonSpeechToTextCustomModelHealthCheckTest::handle);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        REQUESTS.add(path);
        int status;
        String response;
        if (path.equals("/v1/models")) {
            status = modelsStatus;
            response = status == 200 ? "{\"models\":[]}" : "{\"code\":" + status + ",\"error\":\"service unavailable\"}";
        } else if (path.startsWith("/v1/customizations/")) {
            status = 200;
            String id = path.substring("/v1/customizations/".length());
            response = "{\"customization_id\":\"" + id + "\",\"name\":\"custom model\"}";
        } else {
            status = 404;
            response = "{\"code\":404,\"error\":\"not found\"}";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @BeforeEach
    void reset() {
        REQUESTS.clear();
        modelsStatus = 200;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        HealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        registry.setCamelContext(context);
        HealthCheckRepository producers = (HealthCheckRepository) registry.resolveById("producers");
        producers.setEnabled(true);
        registry.register(producers);
        context.getCamelContextExtension().addContextPlugin(HealthCheckRegistry.class, registry);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                WatsonSpeechToTextConfiguration configuration = new WatsonSpeechToTextConfiguration();
                configuration.setApiKey("test-key");
                configuration.setOperation(WatsonSpeechToTextOperations.getCustomModel);
                WatsonSpeechToTextComponent component
                        = getContext().getComponent("ibm-watson-speech-to-text", WatsonSpeechToTextComponent.class);
                from("direct:getCustomModel")
                        .to(new TestServerEndpoint("ibm-watson-speech-to-text:test", component, configuration));
            }
        };
    }

    @Test
    void theCustomizationIdHeaderSelectsTheCustomModel() {
        Exchange result = getCustomModel(Map.of(CUSTOMIZATION_ID_HEADER, "guid-1"));

        assertFalse(result.isFailed(), () -> "failed: " + result.getException());
        assertEquals("guid-1", assertInstanceOf(LanguageModel.class, result.getMessage().getBody()).getCustomizationId());
        assertEquals(List.of("/v1/customizations/guid-1"), REQUESTS);
    }

    @Test
    void theCustomizationIdHeaderComesBeforeTheModelNameHeader() {
        Exchange result = getCustomModel(Map.of(CUSTOMIZATION_ID_HEADER, "guid-1",
                WatsonSpeechToTextConstants.MODEL_NAME, "guid-2"));

        assertFalse(result.isFailed(), () -> "failed: " + result.getException());
        assertEquals(List.of("/v1/customizations/guid-1"), REQUESTS);
    }

    @Test
    void theModelNameHeaderIsStillTheCustomizationIdWithoutTheNewHeader() {
        Exchange result = getCustomModel(Map.of(WatsonSpeechToTextConstants.MODEL_NAME, "guid-2"));

        assertFalse(result.isFailed(), () -> "failed: " + result.getException());
        assertEquals(List.of("/v1/customizations/guid-2"), REQUESTS);
    }

    @Test
    void theProducerHealthCheckListsTheModels() {
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            HealthCheck.Result result = producerCheck();
            assertEquals(HealthCheck.State.UP, result.getState());
        });
        assertEquals("/v1/models", REQUESTS.get(REQUESTS.size() - 1));

        modelsStatus = 503;
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            HealthCheck.Result result = producerCheck();
            assertEquals(HealthCheck.State.DOWN, result.getState());
            assertEquals(503, result.getDetails().get(AbstractHealthCheck.SERVICE_STATUS_CODE));
        });
    }

    private HealthCheck.Result producerCheck() {
        HealthCheck.Result result = HealthCheckHelper.invokeReadiness(context).stream()
                .filter(r -> r.getCheck().getId().startsWith("producer:ibm-watson-speech-to-text"))
                .findFirst().orElse(null);
        assertNotNull(result, "no producer health check of ibm-watson-speech-to-text");
        return result;
    }

    private Exchange getCustomModel(Map<String, Object> headers) {
        return template.request("direct:getCustomModel", e -> e.getIn().getHeaders().putAll(headers));
    }

    private static final class TestServerEndpoint extends WatsonSpeechToTextEndpoint {

        private final SpeechToText client;

        TestServerEndpoint(String uri, WatsonSpeechToTextComponent component, WatsonSpeechToTextConfiguration configuration) {
            super(uri, component, configuration);
            client = new SpeechToText(new NoAuthAuthenticator());
            client.setServiceUrl("http://localhost:" + server.getAddress().getPort());
        }

        @Override
        public SpeechToText getSttClient() {
            return client;
        }
    }
}
