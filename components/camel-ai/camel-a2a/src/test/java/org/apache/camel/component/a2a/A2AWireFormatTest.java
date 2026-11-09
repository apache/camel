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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.a2a.model.AgentCard;
import org.apache.camel.component.a2a.model.Artifact;
import org.apache.camel.component.a2a.model.AuthenticationInfo;
import org.apache.camel.component.a2a.model.DataPart;
import org.apache.camel.component.a2a.model.FilePart;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.SendMessageConfiguration;
import org.apache.camel.component.a2a.model.SendMessageRequest;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskArtifactUpdateEvent;
import org.apache.camel.component.a2a.model.TaskPushNotificationConfig;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TaskStatusUpdateEvent;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.component.a2a.protocol.JsonRpcProtocol;
import org.apache.camel.component.a2a.protocol.SseCodec;
import org.apache.camel.component.a2a.util.A2AJsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the exact JSON of the A2A protocol. The expected values are the output of the Jackson 2 mapper the component
 * used before moving to Jackson 3: a change here is a change to the wire format.
 */
class A2AWireFormatTest {

    private static final OffsetDateTime TIMESTAMP = OffsetDateTime.parse("2026-03-01T10:00:00.5+02:00");

    private static final String MESSAGE_JSON = "{\"role\":\"ROLE_USER\",\"parts\":["
                                               + "{\"text\":\"hi\",\"metadata\":null},"
                                               + "{\"data\":{\"z\":1,\"a\":1.5},\"metadata\":null},"
                                               + "{\"url\":\"https://host/a.png\",\"mediaType\":\"image/png\",\"filename\":\"a.png\"}],"
                                               + "\"messageId\":\"m1\",\"contextId\":\"c1\",\"taskId\":\"t1\","
                                               + "\"referenceTaskIds\":[\"r1\"],\"metadata\":{\"k\":\"v\"},\"extensions\":[\"e1\"]}";

    private static final String ARTIFACT_JSON = "{\"artifactId\":\"a1\",\"name\":\"n\",\"description\":\"d\","
                                                + "\"parts\":[{\"text\":\"x\",\"metadata\":null}],"
                                                + "\"metadata\":{\"k\":\"v\"},\"extensions\":[\"e1\"]}";

    private static final String TASK_JSON
            = "{\"id\":\"t1\",\"contextId\":\"c1\",\"status\":{\"state\":\"TASK_STATE_COMPLETED\","
              + "\"message\":" + MESSAGE_JSON + ","
              + "\"timestamp\":\"2026-03-01T10:00:00.5+02:00\"},"
              + "\"artifacts\":[" + ARTIFACT_JSON + "],"
              + "\"history\":[" + MESSAGE_JSON + "],"
              + "\"metadata\":{\"k\":\"v\"}}";

    private static Message message() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("z", 1);
        data.put("a", 1.50);
        return Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("hi"), new DataPart(data),
                        FilePart.ofUrl("https://host/a.png", "image/png", "a.png")))
                .messageId("m1")
                .contextId("c1")
                .taskId("t1")
                .referenceTaskIds(List.of("r1"))
                .metadata(Map.of("k", "v"))
                .extensions(List.of("e1"))
                .build();
    }

    private static Artifact artifact() {
        return Artifact.builder()
                .artifactId("a1")
                .name("n")
                .description("d")
                .parts(List.of(new TextPart("x")))
                .metadata(Map.of("k", "v"))
                .extensions(List.of("e1"))
                .build();
    }

    private static Task task() {
        return Task.builder()
                .id("t1")
                .contextId("c1")
                .status(new TaskStatus(TaskState.COMPLETED, message(), TIMESTAMP))
                .artifacts(List.of(artifact()))
                .history(List.of(message()))
                .metadata(Map.of("k", "v"))
                .build();
    }

    private static String write(Object value) throws Exception {
        return A2AJsonMapper.instance().writeValueAsString(value);
    }

    private static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String nestedArray(int depth) {
        return "[".repeat(depth) + "]".repeat(depth);
    }

    @Test
    void writesTaskWithPropertiesInDeclarationOrder() throws Exception {
        assertThat(write(task())).isEqualTo(TASK_JSON);
    }

    @Test
    void writesStreamingEvents() throws Exception {
        assertThat(write(StreamResponse.ofStatusUpdate(TaskStatusUpdateEvent.builder()
                .taskId("t1")
                .contextId("c1")
                .status(new TaskStatus(TaskState.FAILED, null, TIMESTAMP))
                .metadata(Map.of("k", "v"))
                .build())))
                .isEqualTo("{\"statusUpdate\":{\"taskId\":\"t1\",\"contextId\":\"c1\",\"status\":"
                           + "{\"state\":\"TASK_STATE_FAILED\",\"timestamp\":\"2026-03-01T10:00:00.5+02:00\"},"
                           + "\"metadata\":{\"k\":\"v\"}}}");
        assertThat(write(StreamResponse.ofArtifactUpdate(TaskArtifactUpdateEvent.builder()
                .taskId("t1")
                .contextId("c1")
                .artifact(artifact())
                .append(true)
                .lastChunk(false)
                .build())))
                .isEqualTo("{\"artifactUpdate\":{\"taskId\":\"t1\",\"contextId\":\"c1\",\"artifact\":" + ARTIFACT_JSON
                           + ",\"append\":true,\"lastChunk\":false}}");
    }

    @Test
    void writesEnumsWithTheirProtocolNames() throws Exception {
        assertThat(write(TaskState.INPUT_REQUIRED)).isEqualTo("\"TASK_STATE_INPUT_REQUIRED\"");
        assertThat(write(Message.Role.ROLE_AGENT)).isEqualTo("\"ROLE_AGENT\"");
    }

    @Test
    void writesTimestamps() throws Exception {
        assertThat(write(new TaskStatus(TaskState.WORKING, null, OffsetDateTime.parse("2026-03-01T10:00Z"))))
                .isEqualTo("{\"state\":\"TASK_STATE_WORKING\",\"timestamp\":\"2026-03-01T10:00:00Z\"}");
        assertThat(write(new TaskStatus(TaskState.WORKING, null, OffsetDateTime.parse("2026-03-01T10:00:00.100-05:30"))))
                .isEqualTo("{\"state\":\"TASK_STATE_WORKING\",\"timestamp\":\"2026-03-01T10:00:00.1-05:30\"}");
    }

    @Test
    void writesUserDataUnchanged() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", "http://host/path?a=b&c=<d>");
        data.put("nothing", null);
        data.put("decimal", new BigDecimal("1.500"));
        data.put("offset", OffsetDateTime.parse("2026-03-01T10:00:00+02:00"));
        data.put("instant", Instant.parse("2026-03-01T10:00:00Z"));
        data.put("date", new Date(0));
        data.put("local", LocalDate.of(2026, 3, 1));
        data.put("duration", Duration.ofSeconds(5));
        data.put("enum", Thread.State.NEW);
        data.put("bytes", new byte[] { 1, 2, 3 });

        assertThat(write(data)).isEqualTo("{\"url\":\"http://host/path?a=b&c=<d>\",\"nothing\":null,\"decimal\":1.500,"
                                          + "\"offset\":\"2026-03-01T10:00:00+02:00\",\"instant\":\"2026-03-01T10:00:00Z\","
                                          + "\"date\":\"1970-01-01T00:00:00.000+00:00\",\"local\":\"2026-03-01\","
                                          + "\"duration\":5.000000000,\"enum\":\"NEW\",\"bytes\":\"AQID\"}");
    }

    @Test
    void doesNotWriteSecretsOfPushConfiguration() throws Exception {
        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setId("i1");
        config.setTaskId("t1");
        config.setUrl("https://host/hook");
        config.setToken("secret-token");
        config.setAuthentication(new AuthenticationInfo("Bearer", "secret-credentials"));

        assertThat(write(config)).isEqualTo("{\"id\":\"i1\",\"taskId\":\"t1\",\"url\":\"https://host/hook\","
                                            + "\"authentication\":{\"scheme\":\"Bearer\"}}");
    }

    @Test
    void writesEnvelopes() {
        JsonRpcProtocol protocol = new JsonRpcProtocol();

        assertThat(utf8(protocol.wrapJsonRpcResponse(task(), "req-1")))
                .isEqualTo("{\"jsonrpc\":\"2.0\",\"result\":" + TASK_JSON + ",\"id\":\"req-1\"}");
        assertThat(utf8(protocol.wrapJsonRpcError(-32602, "bad", 7)))
                .isEqualTo("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32602,\"message\":\"bad\"},\"id\":7}");
        assertThat(SseCodec.encode(StreamResponse.ofTask(task())))
                .isEqualTo("data: {\"task\":" + TASK_JSON + "}\n\n");
    }

    @Test
    void roundTripsAgentCard() throws Exception {
        String json = "{\"zzz\":1,\"name\":\"n\",\"url\":\"http://host/\",\"version\":\"1\","
                      + "\"capabilities\":{\"streaming\":true,\"pushNotifications\":false,\"unknown\":true},"
                      + "\"skills\":[{\"id\":\"s\",\"name\":\"n\",\"tags\":[\"t\"],\"unknown\":true}],"
                      + "\"supportedInterfaces\":[{\"url\":\"http://host/\",\"protocolBinding\":\"jsonrpc\","
                      + "\"protocolVersion\":\"1.0\"}],"
                      + "\"securitySchemes\":{\"k\":{\"apiKeySecurityScheme\":{\"name\":\"X\",\"location\":\"header\"}}},"
                      + "\"securityRequirements\":[{\"schemes\":{\"k\":[\"a\"]}}],"
                      + "\"defaultInputModes\":[\"text\"],\"aaa\":{\"b\":[1]}}";

        assertThat(write(A2AJsonMapper.instance().readValue(json, AgentCard.class)))
                .isEqualTo("{\"name\":\"n\",\"url\":\"http://host/\",\"version\":\"1\","
                           + "\"capabilities\":{\"streaming\":true,\"pushNotifications\":false},"
                           + "\"skills\":[{\"id\":\"s\",\"name\":\"n\",\"description\":null,\"tags\":[\"t\"],"
                           + "\"examples\":null,\"inputModes\":null,\"outputModes\":null,\"securityRequirements\":null}],"
                           + "\"supportedInterfaces\":[{\"url\":\"http://host/\",\"protocolBinding\":\"JSONRPC\","
                           + "\"protocolVersion\":\"1.0\"}],"
                           + "\"securitySchemes\":{\"k\":{\"apiKeySecurityScheme\":{\"location\":\"header\",\"name\":\"X\"}}},"
                           + "\"securityRequirements\":[{\"schemes\":{\"k\":{\"list\":[\"a\"]}}}],"
                           + "\"defaultInputModes\":[\"text\"],\"zzz\":1,\"aaa\":{\"b\":[1]}}");
    }

    @Test
    void readsUnknownPropertiesAsBefore() throws Exception {
        var mapper = A2AJsonMapper.instance();

        Task task = mapper.readValue(
                "{\"id\":\"t1\",\"unknown\":1,\"status\":{\"state\":\"TASK_STATE_WORKING\",\"extra\":{\"a\":1}}}", Task.class);
        assertThat(task.id()).isEqualTo("t1");
        assertThat(task.status().state()).isEqualTo(TaskState.WORKING);

        SendMessageRequest request = mapper.readValue(
                "{\"unknown\":1,\"configuration\":{\"blocking\":true,\"zz\":1}}", SendMessageRequest.class);
        SendMessageConfiguration configuration = request.getConfiguration();
        assertThat(configuration.getBlocking()).isTrue();
        assertThat(configuration.getAdditionalProperties()).containsEntry("zz", 1);
    }

    @Test
    void readsLenientlyAsBefore() throws Exception {
        var mapper = A2AJsonMapper.instance();

        assertThat(mapper.readValue("{\"id\":\"t1\"} trailing", Task.class).id()).isEqualTo("t1");
        assertThat(mapper.readValue("{\"id\":5}", Task.class).id()).isEqualTo("5");
        assertThat(mapper.readValue("{\"state\":\"BOGUS\"}", TaskStatus.class).state()).isEqualTo(TaskState.UNSPECIFIED);
        assertThat(mapper.readValue("{\"role\":\"user\"}", Message.class).role()).isEqualTo(Message.Role.ROLE_USER);
    }

    @Test
    void readsTimestamps() throws Exception {
        var mapper = A2AJsonMapper.instance();

        // an offset is normalized to UTC when read
        assertThat(write(mapper.readValue("{\"timestamp\":\"2026-03-01T10:00:00+02:00\"}", TaskStatus.class)))
                .isEqualTo("{\"timestamp\":\"2026-03-01T08:00:00Z\"}");
        assertThat(write(mapper.readValue("{\"timestamp\":1772359200}", TaskStatus.class)))
                .isEqualTo("{\"timestamp\":\"2026-03-01T10:00:00Z\"}");
    }

    @Test
    void readsPartContentAsBefore() throws Exception {
        var mapper = A2AJsonMapper.instance();

        assertThat(textOf(mapper.readValue("{\"parts\":[{\"text\":5}]}", Message.class))).isEqualTo("5");
        assertThat(textOf(mapper.readValue("{\"parts\":[{\"text\":true}]}", Message.class))).isEqualTo("true");
        assertThat(textOf(mapper.readValue("{\"parts\":[{\"text\":{\"a\":1}}]}", Message.class))).isEmpty();
        assertThat(textOf(mapper.readValue("{\"parts\":[{\"text\":[1]}]}", Message.class))).isEmpty();
        assertThat(textOf(mapper.readValue("{\"parts\":[{\"text\":null}]}", Message.class))).isNull();

        assertThatThrownBy(() -> mapper.readValue("{\"parts\":[{}]}", Message.class))
                .hasMessageContaining("Cannot determine A2A part kind");
        assertThatThrownBy(() -> mapper.readValue("{\"parts\":[{\"text\":\"a\",\"data\":1}]}", Message.class))
                .hasMessageContaining("exactly one content field");
    }

    @Test
    void usesTheNestingDepthLimitOfJackson3() throws Exception {
        var mapper = A2AJsonMapper.instance();

        Object nested = mapper.readValue(nestedArray(500), Object.class);
        assertThat(mapper.writeValueAsString(nested)).isEqualTo(nestedArray(500));
        assertThatThrownBy(() -> mapper.readValue(nestedArray(501), Object.class))
                .hasMessageContaining("nesting depth");
    }

    private static String textOf(Message message) {
        return ((TextPart) message.parts().get(0)).text();
    }
}
