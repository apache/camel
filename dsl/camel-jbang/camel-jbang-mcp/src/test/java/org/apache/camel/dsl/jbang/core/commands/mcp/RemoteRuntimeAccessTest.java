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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolContext;
import org.apache.camel.dsl.jbang.core.common.LocalRuntimeAccess;
import org.apache.camel.dsl.jbang.core.common.RuntimeAccess;
import org.apache.camel.dsl.jbang.core.common.RuntimeTarget;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteRuntimeAccessTest {

    @Test
    void remoteTargetUsesExecutionIdRatherThanPid() {
        RemoteRuntimeEntry entry = entry("test-owner");

        RuntimeTarget target = RemoteRuntimeAccess.toTarget(entry);

        assertThat(target.id()).isEqualTo("websocket:execution-42");
        assertThat(target.pid()).isNull();
        assertThat(target.name()).isEqualTo("remote-orders");
        assertThat(target.contextName()).isEqualTo("orders-context");
        assertThat(target.transport()).isEqualTo("websocket");
    }

    @Test
    void readsStatusAndReceiveSnapshotsUsingOpaqueTarget() {
        RemoteRuntimeConfig config = new RemoteRuntimeConfig();
        config.enabled = true;
        config.token = "phase3-test-token";
        RemoteRuntimeEntry entry = entry(config.ownerId());
        final String expectedOwnerId = config.ownerId();
        JsonObject status = new JsonObject();
        JsonObject context = new JsonObject();
        context.put("name", "orders-context");
        status.put("context", context);
        entry.updateSnapshot("status", status);

        JsonObject first = new JsonObject();
        first.put("uid", 41);
        first.put("body", "before");
        JsonObject second = new JsonObject();
        second.put("uid", 42);
        second.put("body", "after");
        JsonObject receive = new JsonObject();
        receive.put("messages", new JsonArray(List.of(first, second)));
        entry.updateSnapshot("receive", receive);

        RemoteRuntimeRegistry registry = new RemoteRuntimeRegistry() {
            @Override
            RemoteRuntimeEntry find(String ownerId, String executionId) {
                return "execution-42".equals(executionId) && expectedOwnerId.equals(ownerId) ? entry : null;
            }
        };
        RemoteRuntimeAccess access = new RemoteRuntimeAccess(LocalRuntimeAccess.INSTANCE, registry, config);
        RuntimeTarget target = RemoteRuntimeAccess.toTarget(entry);

        assertThat(access.readStatus(target)).isEqualTo(status);
        assertThat(access.receiveCursor(target)).isEqualTo("42");
        List<JsonObject> received = access.readReceivedMessages(target, "41");
        assertThat(received).hasSize(1);
        assertThat(received.get(0).getString("uid")).isEqualTo("42");
        assertThat(received.get(0).getString("body")).isEqualTo("after");

        RuntimeService service = new RuntimeService();
        service.runtimeAccess = access;
        RuntimeService.ProcessInfo process = RuntimeService.ProcessInfo.from(target);
        assertThat(process.pid()).isNull();
        assertThat(process.id()).isEqualTo("websocket:execution-42");
        ToolContext toolContext = service.newToolContext();
        toolContext.selectProcess(process.target());
        assertThat(toolContext.readFullStatus()).isEqualTo(status);
    }

    @Test
    void toolContextExecutesRemoteActionsAgainstTheStableRuntimeTarget() {
        RemoteRuntimeConfig config = new RemoteRuntimeConfig();
        config.enabled = true;
        config.token = "phase3-action-token";
        RemoteRuntimeEntry entry = connectedEntry(config);
        final String expectedOwnerId = config.ownerId();
        RemoteRuntimeRegistry registry = new RemoteRuntimeRegistry() {
            @Override
            RemoteRuntimeEntry find(String ownerId, String executionId) {
                return expectedOwnerId.equals(ownerId) && "execution-42".equals(executionId) ? entry : null;
            }
        };
        RemoteRuntimeAccess access = new RemoteRuntimeAccess(LocalRuntimeAccess.INSTANCE, registry, config);
        ToolContext context = new ToolContext(access);
        context.selectProcess(RemoteRuntimeAccess.toTarget(entry));

        assertThat(context.executeAction("route", request -> {
            request.put("id", "orders");
            request.put("command", "start");
        })).contains("started");
    }

    @Test
    void discoversOnlyReadyRemoteRuntimesInTheConfiguredOwnerNamespace() {
        RemoteRuntimeConfig config = new RemoteRuntimeConfig();
        config.enabled = true;
        config.token = "phase3-discovery-token";
        RemoteRuntimeEntry entry = entry(config.ownerId());
        final String expectedOwnerId = config.ownerId();
        RemoteRuntimeConnection connection = new RemoteRuntimeConnection(
                (WebSocketConnection) Proxy.newProxyInstance(
                        WebSocketConnection.class.getClassLoader(), new Class<?>[] { WebSocketConnection.class },
                        (proxy, method, args) -> switch (method.getName()) {
                            case "isOpen" -> true;
                            case "toString" -> "Open test WebSocket";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        }),
                config);
        entry.attach(connection);
        connection.attach(entry);
        JsonObject hello = CliConnectorProtocol.envelope("hello");
        hello.put("camelVersion", "4.24.0-SNAPSHOT");
        hello.put("name", "remote-orders");
        hello.put("transport", "jbang");
        hello.put("runtime", new JsonObject());
        connection.acceptText(hello.toJson());

        RemoteRuntimeRegistry registry = new RemoteRuntimeRegistry() {
            @Override
            List<RemoteRuntimeEntry> list(String ownerId) {
                return expectedOwnerId.equals(ownerId) ? List.of(entry) : List.of();
            }

            @Override
            RemoteRuntimeEntry find(String ownerId, String executionId) {
                return expectedOwnerId.equals(ownerId) && entry.executionId().equals(executionId) ? entry : null;
            }
        };
        RuntimeAccess noLocalProcesses = (RuntimeAccess) Proxy.newProxyInstance(
                RuntimeAccess.class.getClassLoader(), new Class<?>[] { RuntimeAccess.class },
                (proxy, method, args) -> {
                    if ("discoverProcesses".equals(method.getName())) {
                        return List.of();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        RemoteRuntimeAccess access = new RemoteRuntimeAccess(noLocalProcesses, registry, config);

        assertThat(access.discoverProcesses())
                .singleElement()
                .extracting(RuntimeTarget::id)
                .isEqualTo("websocket:execution-42");
        assertThat(registry.list("another-owner")).isEmpty();
    }

    @Test
    void runtimeToolsDelegateToTheRemoteTargetWithoutAPid() {
        RuntimeTarget target = new RuntimeTarget(
                "websocket:execution-42", null,
                "remote-orders", "orders-context", "websocket");
        JsonObject status = new JsonObject();
        JsonObject contextStatus = new JsonObject();
        contextStatus.put("name", "orders-context");
        contextStatus.put("state", "Started");
        status.put("context", contextStatus);

        RuntimeService service = new RuntimeService();
        service.runtimeAccess = runtimeAccess(target, status);
        RuntimeTools runtimeTools = new RuntimeTools();
        runtimeTools.runtimeService = service;

        JsonObject answer = runtimeTools.camel_runtime_context("remote-orders");

        assertThat(answer.getString("name")).isEqualTo("orders-context");
        assertThat(answer.getString("state")).isEqualTo("Started");
    }

    @Test
    void aiTraceResultCarriesTheRemoteIdentityInsteadOfAnOperatingSystemPid() {
        RuntimeTarget target = new RuntimeTarget(
                "websocket:execution-42", null,
                "remote-orders", "orders-context", "websocket");
        RuntimeService service = new RuntimeService();
        service.runtimeAccess = runtimeAccess(target, new JsonObject());
        AiTraceTools aiTraceTools = new AiTraceTools();
        aiTraceTools.runtimeService = service;

        AiTraceTools.AiTraceResult result = aiTraceTools.camel_runtime_ai_trace("remote-orders", "*");

        assertThat(result.processName()).isEqualTo("remote-orders");
        assertThat(result.pid()).isNull();
        assertThat(result.targetId()).isEqualTo("websocket:execution-42");
        assertThat(result.transport()).isEqualTo("websocket");
    }

    @Test
    void remoteReceiveReturnsOnlyMessagesAfterTheCursorAcrossSnapshotReplay() {
        RemoteRuntimeConfig config = new RemoteRuntimeConfig();
        config.enabled = true;
        config.token = "phase4-receive-test-token";
        String expectedOwnerId = config.ownerId();
        RemoteRuntimeEntry entry = entry(expectedOwnerId);
        entry.updateSnapshot("receive", receive(10, 11));

        RemoteRuntimeRegistry registry = new RemoteRuntimeRegistry() {
            @Override
            RemoteRuntimeEntry find(String ownerId, String executionId) {
                return expectedOwnerId.equals(ownerId) && "execution-42".equals(executionId) ? entry : null;
            }
        };
        RemoteRuntimeAccess access = new RemoteRuntimeAccess(LocalRuntimeAccess.INSTANCE, registry, config);
        RuntimeTarget target = RemoteRuntimeAccess.toTarget(entry);

        assertThat(access.receiveCursor(target)).isEqualTo("11");
        assertThat(access.readReceivedMessages(target, "10"))
                .extracting(message -> message.getLong("uid")).containsExactly(11L);

        // A reconnect replays UID 11 and adds UID 12; replay must not be returned twice.
        entry.updateSnapshot("receive", receive(11, 12));
        assertThat(access.readReceivedMessages(target, "11"))
                .extracting(message -> message.getLong("uid")).containsExactly(12L);
    }

    private static RuntimeAccess runtimeAccess(RuntimeTarget target, JsonObject status) {
        return (RuntimeAccess) Proxy.newProxyInstance(
                RuntimeAccess.class.getClassLoader(), new Class<?>[] { RuntimeAccess.class }, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "discoverProcesses" -> List.of(target);
                        case "findProcess", "findChaosProcess" -> target;
                        case "readStatus" -> status;
                        case "readHistoryFile", "readErrorFile" -> new JsonObject();
                        case "executeActionText" -> "{}";
                        case "runtimeVersion" -> "4.24.0-SNAPSHOT";
                        case "toString" -> "RemoteRuntimeAccessTestProxy";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(
                                "Unexpected RuntimeAccess method: " + method.getName());
                    };
                });
    }

    private static JsonObject receive(long... uids) {
        JsonObject data = new JsonObject();
        JsonArray messages = new JsonArray();
        for (long uid : uids) {
            JsonObject message = new JsonObject();
            message.put("uid", uid);
            message.put("body", "message-" + uid);
            messages.add(message);
        }
        data.put("messages", messages);
        return data;
    }

    private static JsonObject hello(String name) {
        JsonObject hello = CliConnectorProtocol.envelope("hello");
        hello.put("camelVersion", "4.24.0-SNAPSHOT");
        hello.put("name", name);
        hello.put("transport", "jbang");
        hello.put("runtime", new JsonObject());
        return hello;
    }

    private static RemoteRuntimeEntry connectedEntry(RemoteRuntimeConfig config) {
        RemoteRuntimeEntry entry = new RemoteRuntimeEntry(config.ownerId(), "execution-42", 1024 * 1024, 100);
        AtomicReference<RemoteRuntimeConnection> current = new AtomicReference<>();
        WebSocketConnection socket = (WebSocketConnection) Proxy.newProxyInstance(
                WebSocketConnection.class.getClassLoader(), new Class<?>[] { WebSocketConnection.class },
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "isOpen" -> true;
                        case "sendText" -> {
                            JsonObject request = CliConnectorProtocol.parseFrame((String) args[0]);
                            assertThat(request.getString("type")).isEqualTo("action");
                            JsonObject action = (JsonObject) request.get("action");
                            assertThat(action.getString("action")).isEqualTo("route");
                            assertThat(action.getString("id")).isEqualTo("orders");
                            JsonObject response = CliConnectorProtocol.envelope("result");
                            response.put("requestId", request.getString("requestId"));
                            response.put("ok", true);
                            JsonObject result = new JsonObject();
                            result.put("status", "started");
                            response.put("result", result);
                            yield Uni.createFrom().voidItem()
                                    .invoke(() -> current.get().acceptText(response.toJson()));
                        }
                        case "toString" -> "RemoteRuntimeAccessTest WebSocket";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                });
        RemoteRuntimeConnection connection = new RemoteRuntimeConnection(socket, config);
        current.set(connection);
        entry.attach(connection);
        connection.attach(entry);
        connection.acceptText(hello("remote-orders").toJson());
        return entry;
    }

    private static RemoteRuntimeEntry entry(String ownerId) {
        RemoteRuntimeEntry entry = new RemoteRuntimeEntry(ownerId, "execution-42", 1024 * 1024, 100);
        entry.updateHello(hello("remote-orders"));
        JsonObject status = new JsonObject();
        JsonObject context = new JsonObject();
        context.put("name", "orders-context");
        status.put("context", context);
        entry.updateSnapshot("status", status);
        return entry;
    }
}
