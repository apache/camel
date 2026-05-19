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
package org.apache.camel.component.a2a;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class A2AConsumerHttpIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private int port;
    private CamelContext context;
    private HttpClient http;

    @BeforeEach
    void setUp() {
        port = AvailablePortFinder.getNextAvailable();
        http = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context != null) {
            context.stop();
        }
    }

    private CamelContext createContext() throws Exception {
        VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
        conf.setBindPort(port);
        CamelContext ctx = new DefaultCamelContext();
        ctx.getShutdownStrategy().setTimeout(5);
        ctx.addService(new VertxPlatformHttpServer(conf));
        // Trigger platform-http component creation so RestConsumerFactory is discoverable
        ctx.getComponent("platform-http");
        return ctx;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return post(path, body, A2AConstants.CONTENT_TYPE);
    }

    private HttpResponse<String> post(String path, String body, String contentType) throws Exception {
        return http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + path))
                        .header("Content-Type", contentType)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl() + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    void agentCardEndpoint() throws Exception {
        context = createContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("a2a:test-agent?name=TestAgent&version=1.0.0&description=Integration+test+agent")
                        .setBody(constant("echo"));
            }
        });
        context.start();

        HttpResponse<String> response = get("/.well-known/agent-card.json");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode card = MAPPER.readTree(response.body());
        assertThat(card.get("name").asText()).isEqualTo("TestAgent");
        assertThat(card.get("version").asText()).isEqualTo("1.0.0");
    }

    @Test
    void sendMessageViaRest() throws Exception {
        context = createContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("a2a:test-agent?name=EchoAgent&version=1.0.0&validateAuth=false")
                        .setBody(constant("Echo response"));
            }
        });
        context.start();

        HttpResponse<String> response = post("/message:send",
                "{\"message\":{\"messageId\":\"msg-1\",\"role\":\"user\",\"parts\":[{\"text\":\"Hello\"}]}}");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = MAPPER.readTree(response.body());
        assertThat(body.has("task")).isTrue();
        assertThat(body.get("task").get("id").asText()).isNotEmpty();
        assertThat(body.get("task").get("contextId").asText()).isNotEmpty();
    }

    @Test
    void getTaskViaRest() throws Exception {
        context = createContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("a2a:test-agent?name=TaskAgent&version=1.0.0&validateAuth=false")
                        .setBody(constant("response"));
            }
        });
        context.start();

        HttpResponse<String> createResp = post("/message:send",
                "{\"message\":{\"messageId\":\"msg-get\",\"role\":\"user\",\"parts\":[{\"text\":\"Hi\"}]}}");
        String taskId = MAPPER.readTree(createResp.body()).get("task").get("id").asText();

        HttpResponse<String> getResp = get("/tasks/" + taskId);

        assertThat(getResp.statusCode()).isEqualTo(200);
        assertThat(MAPPER.readTree(getResp.body()).get("id").asText()).isEqualTo(taskId);
    }

    @Test
    void getTaskReturns404() throws Exception {
        context = createContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("a2a:test-agent?name=TaskAgent&version=1.0.0&validateAuth=false")
                        .setBody(constant("response"));
            }
        });
        context.start();

        HttpResponse<String> response = get("/tasks/nonexistent");

        assertThat(response.statusCode()).isEqualTo(404);
        JsonNode error = MAPPER.readTree(response.body()).get("error");
        assertThat(error.get("details").get(0).get("reason").asText()).isEqualTo("TASK_NOT_FOUND");
    }

    @Test
    void cancelTaskViaRest() throws Exception {
        context = createContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("a2a:test-agent?name=CancelAgent&version=1.0.0&returnImmediately=true&asyncTimeout=30000&validateAuth=false")
                        .delay(10000)
                        .setBody(constant("in progress"));
            }
        });
        context.start();

        HttpResponse<String> createResp = post("/message:send",
                "{\"message\":{\"messageId\":\"msg-cancel\",\"role\":\"user\",\"parts\":[{\"text\":\"work\"}]}}");
        String taskId = MAPPER.readTree(createResp.body()).get("task").get("id").asText();

        HttpResponse<String> cancelResp = post("/tasks/" + taskId + ":cancel", "");

        assertThat(cancelResp.statusCode()).isEqualTo(200);
        JsonNode task = MAPPER.readTree(cancelResp.body());
        assertThat(task.get("id").asText()).isEqualTo(taskId);
        assertThat(task.get("status").get("state").asText()).isEqualTo("TASK_STATE_CANCELED");
    }

    @Test
    void listTasksViaRest() throws Exception {
        context = createContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("a2a:test-agent?name=ListAgent&version=1.0.0&validateAuth=false")
                        .setBody(constant("done"));
            }
        });
        context.start();

        for (int i = 0; i < 2; i++) {
            post("/message:send",
                    "{\"message\":{\"messageId\":\"msg-list-" + i
                                  + "\",\"role\":\"user\",\"parts\":[{\"text\":\"item\"}]}}");
        }

        HttpResponse<String> listResp = get("/tasks");

        assertThat(listResp.statusCode()).isEqualTo(200);
        JsonNode root = MAPPER.readTree(listResp.body());
        assertThat(root.isObject()).isTrue();
        JsonNode tasks = root.get("tasks");
        assertThat(tasks).isNotNull();
        assertThat(tasks.isArray()).isTrue();
        assertThat(tasks.size()).isEqualTo(2);
    }

    @Test
    void sendMessageViaJsonRpc() throws Exception {
        context = createContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("a2a:test-agent?name=JsonRpcAgent&version=1.0.0&protocolBinding=jsonrpc&validateAuth=false")
                        .setBody(constant("JSON-RPC echo"));
            }
        });
        context.start();

        String jsonRpcRequest = "{\"jsonrpc\":\"2.0\",\"method\":\"SendMessage\","
                                + "\"params\":{\"message\":{\"messageId\":\"msg-jrpc\","
                                + "\"role\":\"user\",\"parts\":[{\"text\":\"Hello JSON-RPC\"}]}},"
                                + "\"id\":\"req-1\"}";

        HttpResponse<String> response = post("/", jsonRpcRequest, A2AConstants.JSONRPC_CONTENT_TYPE);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = MAPPER.readTree(response.body());
        assertThat(body.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(body.get("result").get("task").get("id").asText()).isNotEmpty();
        assertThat(body.get("id").asText()).isEqualTo("req-1");
    }

    @Test
    void agentCardPublicWhenAuthEnabled() throws Exception {
        context = createContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("a2a:test-agent?name=SecureAgent&version=1.0.0&validateAuth=true&apiKey=secret-key")
                        .setBody(constant("secured"));
            }
        });
        context.start();

        HttpResponse<String> response = get("/.well-known/agent-card.json");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(MAPPER.readTree(response.body()).get("name").asText()).isEqualTo("SecureAgent");
    }

    @Test
    void pushConfigCrudViaRest() throws Exception {
        context = createContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("a2a:classpath:cards/push-agent-card.json?validateAuth=false")
                        .setBody(constant("push response"));
            }
        });
        context.start();

        HttpResponse<String> createTaskResp = post("/message:send",
                "{\"message\":{\"messageId\":\"msg-push\",\"role\":\"user\",\"parts\":[{\"text\":\"push\"}]}}");
        String taskId = MAPPER.readTree(createTaskResp.body()).get("task").get("id").asText();

        HttpResponse<String> createConfigResp = post(
                "/tasks/" + taskId + "/pushNotificationConfigs",
                "{\"url\":\"https://example.com/webhook\"}");
        assertThat(createConfigResp.statusCode()).isEqualTo(200);
        String configId = MAPPER.readTree(createConfigResp.body()).get("id").asText();
        assertThat(configId).isNotEmpty();

        HttpResponse<String> listResp = get("/tasks/" + taskId + "/pushNotificationConfigs");
        assertThat(MAPPER.readTree(listResp.body()).size()).isEqualTo(1);

        HttpResponse<String> getResp = get("/tasks/" + taskId + "/pushNotificationConfigs/" + configId);
        assertThat(getResp.statusCode()).isEqualTo(200);
        assertThat(MAPPER.readTree(getResp.body()).get("id").asText()).isEqualTo(configId);

        HttpResponse<String> deleteResp = delete("/tasks/" + taskId + "/pushNotificationConfigs/" + configId);
        assertThat(deleteResp.statusCode()).isEqualTo(200);

        HttpResponse<String> getAfterDelete = get("/tasks/" + taskId + "/pushNotificationConfigs/" + configId);
        assertThat(getAfterDelete.statusCode()).isEqualTo(404);
    }

    @Test
    void authRejectionReturns401() throws Exception {
        context = createContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("a2a:classpath:cards/secure-agent-card.json?apiKey=secret-key")
                        .setBody(constant("secured"));
            }
        });
        context.start();

        HttpResponse<String> response = post("/message:send",
                "{\"message\":{\"messageId\":\"msg-noauth\",\"role\":\"user\",\"parts\":[{\"text\":\"No auth\"}]}}");

        assertThat(response.statusCode()).isEqualTo(401);
        JsonNode error = MAPPER.readTree(response.body()).get("error");
        assertThat(error.get("details").get(0).get("reason").asText()).isEqualTo("AUTHENTICATION");
    }
}
