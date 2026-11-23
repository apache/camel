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
import java.net.SocketTimeoutException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteRuntimeConnectionTest {

    @Test
    void timeoutSendsCorrelatedCancellationFrame() throws Exception {
        BlockingQueue<JsonObject> sent = new LinkedBlockingQueue<>();
        WebSocketConnection socket = (WebSocketConnection) Proxy.newProxyInstance(
                WebSocketConnection.class.getClassLoader(), new Class<?>[] { WebSocketConnection.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "isOpen" -> true;
                    case "sendText" -> Uni.createFrom().voidItem()
                            .invoke(() -> sent.add(CliConnectorProtocol.parseFrame((String) args[0])));
                    case "toString" -> "RemoteRuntimeConnectionTest WebSocket";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });

        RemoteRuntimeConfig config = new RemoteRuntimeConfig();
        RemoteRuntimeEntry entry = new RemoteRuntimeEntry("test-owner", "execution-1", 4096, 10);
        RemoteRuntimeConnection connection = new RemoteRuntimeConnection(socket, config);
        entry.attach(connection);
        connection.attach(entry);

        JsonObject hello = CliConnectorProtocol.envelope("hello");
        hello.put("camelVersion", "4.24.0-SNAPSHOT");
        hello.put("name", "orders");
        hello.put("transport", "test");
        hello.put("runtime", new JsonObject());
        connection.acceptText(hello.toJson());

        JsonObject action = new JsonObject();
        action.put("action", "route");
        action.put("command", "start");

        assertThatThrownBy(() -> connection.executeAction(action, 100))
                .isInstanceOf(SocketTimeoutException.class);

        JsonObject actionFrame = sent.poll(2, TimeUnit.SECONDS);
        JsonObject cancelFrame = sent.poll(2, TimeUnit.SECONDS);
        assertThat(actionFrame).isNotNull();
        assertThat(cancelFrame).isNotNull();
        assertThat(actionFrame.getString("type")).isEqualTo("action");
        assertThat(cancelFrame.getString("type")).isEqualTo("cancel");
        assertThat(cancelFrame.getString("requestId")).isEqualTo(actionFrame.getString("requestId"));
    }
}
