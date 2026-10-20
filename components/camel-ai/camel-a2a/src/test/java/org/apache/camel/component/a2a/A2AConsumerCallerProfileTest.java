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

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.a2a.model.AgentCapabilities;
import org.apache.camel.component.a2a.model.AgentCard;
import org.apache.camel.component.a2a.model.ListTasksResponse;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.SecurityRequirement;
import org.apache.camel.component.a2a.model.SecurityScheme;
import org.apache.camel.component.a2a.model.SendMessageRequest;
import org.apache.camel.component.a2a.model.SendMessageResponse;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TaskStatusUpdateEvent;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.spi.OAuthTokenValidationConfig;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The caller profile of a request reaches the route, and the tasks of a caller are its own.
 */
class A2AConsumerCallerProfileTest extends CamelTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("noopRestConsumerFactory", new NoopRestConsumerFactory());
        context.getRestConfiguration().setComponent("noopRestConsumerFactory");
        return context;
    }

    @Test
    void routeReceivesTheOAuthTokenValidationResult() throws Exception {
        AtomicReference<Object> received = new AtomicReference<>();
        A2AConsumer consumer = createConsumer(exchange -> {
            received.set(exchange.getProperty(A2AConstants.OAUTH_TOKEN_VALIDATION_RESULT));
            exchange.getMessage().setBody("ok");
        });
        OAuthTokenValidationResult result = OAuthTokenValidationResult.valid(
                "user-1", "https://issuer.example", List.of("camel-a2a"), List.of(), Map.of(), 0);

        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(A2AConstants.USER_PROFILE, Map.of("subject", "user-1", "issuer", "https://issuer.example"));
        exchange.setProperty(A2AConstants.OAUTH_TOKEN_VALIDATION_RESULT, result);
        sendMessage(consumer, exchange, "msg-1");

        assertThat(received.get()).isSameAs(result);
    }

    @Test
    void routeReceivesTheOAuthTokenValidationResultWhenStreaming() throws Exception {
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(true);
        A2AConfiguration config = new A2AConfiguration();
        config.setAgentCard(AgentCard.builder().setCapabilities(capabilities).build());

        AtomicReference<Object> received = new AtomicReference<>();
        AtomicReference<A2AConsumer> consumerRef = new AtomicReference<>();
        A2AConsumer consumer = createConsumer(config, exchange -> {
            received.set(exchange.getProperty(A2AConstants.OAUTH_TOKEN_VALIDATION_RESULT));
            // complete the task, which ends the stream
            String taskId = exchange.getMessage().getHeader(A2AConstants.TASK_ID, String.class);
            consumerRef.get().getEndpoint().getTaskStore().notifySubscribers(taskId,
                    StreamResponse.ofStatusUpdate(TaskStatusUpdateEvent.builder().taskId(taskId)
                            .status(new TaskStatus(TaskState.COMPLETED)).build()));
        });
        consumerRef.set(consumer);
        OAuthTokenValidationResult result = OAuthTokenValidationResult.valid(
                "user-1", "https://issuer.example", List.of("camel-a2a"), List.of(), Map.of(), 0);

        consumer.doStart();
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.setProperty(A2AConstants.USER_PROFILE,
                    Map.of("subject", "user-1", "issuer", "https://issuer.example"));
            exchange.setProperty(A2AConstants.OAUTH_TOKEN_VALIDATION_RESULT, result);
            exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(sendMessageRequest("msg-stream")));

            consumer.handleMessageStream(exchange);
            // read the stream to its end: the route has then processed the message
            exchange.getMessage().getBody(InputStream.class).readAllBytes();
        } finally {
            consumer.doStop();
        }

        assertThat(received.get()).isSameAs(result);
    }

    @Test
    void routeDoesNotReceiveTheTokenOfARequirementThatIsNotSatisfied() throws Exception {
        context.getRegistry().bind(OAuthTokenValidationFactory.FACTORY, new StubOAuthTokenValidationFactory());

        Map<String, SecurityScheme> schemes = new LinkedHashMap<>();
        schemes.put("oauth", SecurityScheme.oauth2(null));
        schemes.put("agentKey", SecurityScheme.apiKey("header", "X-Agent-Key"));
        A2AConfiguration config = new A2AConfiguration();
        config.setValidateAuth(true);
        config.setApiKey("secret-key");
        config.setOauthProfile("myprofile");
        config.setAgentCard(AgentCard.builder()
                .setSecuritySchemes(schemes)
                .setSecurityRequirements(List.of(
                        SecurityRequirement.of("oauth", List.of("admin")),
                        SecurityRequirement.of("agentKey", List.of())))
                .build());

        AtomicReference<Object> profile = new AtomicReference<>();
        AtomicReference<Object> result = new AtomicReference<>();
        A2AConsumer consumer = createConsumer(config, exchange -> {
            profile.set(exchange.getProperty(A2AConstants.USER_PROFILE));
            result.set(exchange.getProperty(A2AConstants.OAUTH_TOKEN_VALIDATION_RESULT));
            exchange.getMessage().setBody("ok");
        });

        // the token is valid but lacks the admin scope, so the caller is authenticated with the API key
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("Authorization", "Bearer valid-token");
        exchange.getMessage().setHeader("X-Agent-Key", "secret-key");
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(sendMessageRequest("msg-key")));
        consumer.createDispatchProcessor(consumer::handleSendMessage, false).process(exchange);

        assertThat(profile.get()).isEqualTo(Map.of("scheme", "apiKey"));
        assertThat(result.get()).isNull();
    }

    @Test
    void tasksOfTheClientsOfTokensWithoutSubjectAreSeparate() throws Exception {
        A2AConsumer consumer = createConsumer(exchange -> {
            exchange.getMessage().setHeader(A2AConstants.TASK_STATE, TaskState.WORKING.getProtoName());
            exchange.getMessage().setBody("ok");
        });

        String taskOfA = createTask(consumer, clientProfile("svc-a"));
        String taskOfB = createTask(consumer, clientProfile("svc-b"));

        assertThat(listTasks(consumer, clientProfile("svc-a"))).containsExactly(taskOfA);
        assertThat(listTasks(consumer, clientProfile("svc-b"))).containsExactly(taskOfB);
    }

    private A2AConsumer createConsumer(Processor processor) throws Exception {
        return createConsumer(new A2AConfiguration(), processor);
    }

    private A2AConsumer createConsumer(A2AConfiguration config, Processor processor) throws Exception {
        config.setName("Profile Agent");
        config.setVersion("1.0.0");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AEndpoint endpoint = new A2AEndpoint("a2a:test", component, config);
        endpoint.setAgentCardSource("test");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        return new A2AConsumer(endpoint, processor);
    }

    private static Map<String, Object> clientProfile(String clientId) {
        return Map.of("scheme", "oauth2", "issuer", "https://issuer.example", "claims", Map.of("client_id", clientId));
    }

    private String createTask(A2AConsumer consumer, Map<String, Object> profile) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(A2AConstants.USER_PROFILE, profile);
        return sendMessage(consumer, exchange, "msg-" + profile.hashCode()).getTask().id();
    }

    private static SendMessageRequest sendMessageRequest(String messageId) {
        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(Message.builder()
                .messageId(messageId)
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("hello")))
                .build());
        return request;
    }

    private SendMessageResponse sendMessage(A2AConsumer consumer, Exchange exchange, String messageId) throws Exception {
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(sendMessageRequest(messageId)));

        consumer.handleSendMessage(exchange);

        return OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), SendMessageResponse.class);
    }

    private List<String> listTasks(A2AConsumer consumer, Map<String, Object> profile) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(A2AConstants.USER_PROFILE, profile);

        consumer.handleListTasks(exchange);

        ListTasksResponse response = OBJECT_MAPPER.readValue(
                exchange.getMessage().getBody(byte[].class), ListTasksResponse.class);
        return response.tasks().stream().map(Task::id).toList();
    }

    private static final class StubOAuthTokenValidationFactory implements OAuthTokenValidationFactory {

        @Override
        public OAuthTokenValidationResult validateToken(OAuthTokenValidationConfig config, String token) {
            return validate(token);
        }

        @Override
        public OAuthTokenValidationResult validateToken(CamelContext context, String profileName, String token) {
            return validate(token);
        }

        @Override
        public void validateConfiguration(OAuthTokenValidationConfig config) {
        }

        private static OAuthTokenValidationResult validate(String token) {
            if ("valid-token".equals(token)) {
                return OAuthTokenValidationResult.valid("alice", "https://issuer.example", List.of("camel-a2a"),
                        List.of("message:send"), Map.of(), 1234567890L);
            }
            return OAuthTokenValidationResult.invalid(OAuthTokenValidationResult.ErrorCode.INVALID_TOKEN, "invalid token");
        }
    }
}
