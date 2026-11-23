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

import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteRuntimeRegistryTest {

    @Test
    void reservationsAreUniquePerOwnerAndExecutionId() {
        RemoteRuntimeRegistry registry = new RemoteRuntimeRegistry();
        String reservation = registry.reserve("tenant-a", "orders");

        assertThat(reservation).isNotBlank();
        assertThat(registry.reserve("tenant-a", "orders")).isNull();
        assertThat(registry.reserve("tenant-b", "orders")).isNotBlank();
    }

    @Test
    void reservationsEnforceTheConnectionLimit() {
        RemoteRuntimeRegistry registry = new RemoteRuntimeRegistry();
        for (int i = 0; i < 16; i++) {
            assertThat(registry.reserve("tenant-a", "runtime-" + i)).isNotNull();
        }
        assertThat(registry.reserve("tenant-a", "runtime-over-limit")).isNull();
    }

    @Test
    void reconnectReplacesTheSocketWithoutLosingTheRuntimeEntry() {
        RemoteRuntimeRegistry registry = new RemoteRuntimeRegistry();
        RemoteRuntimeConnection first = connection();
        String firstReservation = registry.reserve("tenant-a", "orders");
        RemoteRuntimeEntry entry = registry.claim(firstReservation, first);

        RemoteRuntimeConnection second = connection();
        String reconnectReservation = registry.reserve("tenant-a", "orders");
        assertThat(reconnectReservation).isNotNull();
        RemoteRuntimeEntry reconnected = registry.claim(reconnectReservation, second);

        assertThat(reconnected).isSameAs(entry);
        assertThat(first.isOpen()).isFalse();
        assertThat(entry.isCurrent(second)).isTrue();

        // A late close callback from the replaced socket must not detach the new session.
        registry.disconnect(entry, first);
        assertThat(entry.isCurrent(second)).isTrue();
        assertThat(entry.isConnected()).isTrue();
    }

    private static RemoteRuntimeConnection connection() {
        WebSocketConnection socket = (WebSocketConnection) Proxy.newProxyInstance(
                WebSocketConnection.class.getClassLoader(), new Class<?>[] { WebSocketConnection.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "isOpen" -> true;
                    case "close" -> Uni.createFrom().voidItem();
                    case "toString" -> "RemoteRuntimeRegistryTest WebSocket";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
        return new RemoteRuntimeConnection(socket, new RemoteRuntimeConfig());
    }

}
