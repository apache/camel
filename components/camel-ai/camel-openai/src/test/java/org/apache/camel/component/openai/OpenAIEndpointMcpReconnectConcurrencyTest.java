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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Concurrency regression tests for CAMEL-23957: MCP reconnect must not corrupt the shared endpoint tool state.
 */
class OpenAIEndpointMcpReconnectConcurrencyTest {

    private static final String SERVER = "test-server";
    private static final List<String> TOOL_NAMES = List.of("get_weather", "find_location");

    private final AtomicInteger clientsCreated = new AtomicInteger();

    private volatile List<String> serverTools = TOOL_NAMES;

    /**
     * Overrides the client-creation seam so reconnects return mocks instead of opening real transports.
     */
    private class TestEndpoint extends OpenAIEndpoint {
        TestEndpoint(OpenAIComponent component) {
            super("openai:chat-completion", component, new OpenAIConfiguration());
        }

        @Override
        McpSyncClient createMcpClient(String serverName, Map<String, String> props) {
            clientsCreated.incrementAndGet();
            return newMockClient();
        }
    }

    private McpSyncClient newMockClient() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.listTools()).thenReturn(McpSchema.ListToolsResult.builder(mockTools()).build());
        return client;
    }

    private List<McpSchema.Tool> mockTools() {
        return serverTools.stream()
                .map(n -> McpSchema.Tool.builder(n, Map.of("type", "object")).description("mock " + n).build())
                .toList();
    }

    private TestEndpoint newEndpoint(McpSyncClient initialClient) {
        DefaultCamelContext ctx = new DefaultCamelContext();
        OpenAIComponent component = new OpenAIComponent();
        component.setCamelContext(ctx);
        TestEndpoint endpoint = new TestEndpoint(component);
        endpoint.setCamelContext(ctx);

        Map<String, McpSyncClient> clientMap = new ConcurrentHashMap<>();
        Map<String, String> toolToServer = new ConcurrentHashMap<>();
        for (String n : serverTools) {
            clientMap.put(n, initialClient);
            toolToServer.put(n, SERVER);
        }

        endpoint.setMcpToolState(new McpToolState(
                McpToolConverter.convert(mockTools()),
                clientMap,
                toolToServer,
                ConcurrentHashMap.newKeySet()));

        Map<String, Map<String, String>> serverConfigs = new ConcurrentHashMap<>();
        serverConfigs.put(SERVER, Map.of("transportType", "stdio"));
        endpoint.setServerConfigs(serverConfigs);

        Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
        locks.put(SERVER, new ReentrantLock());
        endpoint.setMcpClientLocks(locks);

        return endpoint;
    }

    @Test
    void concurrentReadersAndReconnectsDoNotThrow() throws Exception {
        TestEndpoint endpoint = newEndpoint(newMockClient());

        int readers = 8;
        int reconnecters = 4;
        int iterations = 500;
        ExecutorService pool = Executors.newFixedThreadPool(readers + reconnecters);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < readers; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int k = 0; k < iterations; k++) {
                        for (ChatCompletionFunctionTool t : endpoint.getMcpToolState().tools()) {
                            t.function().name();
                        }
                        for (String name : endpoint.getMcpToolState().toolClientMap().keySet()) {
                            endpoint.getMcpToolState().toolClientMap().get(name);
                        }
                        endpoint.getMcpToolState().returnDirectTools().size();
                    }
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }
        for (int i = 0; i < reconnecters; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int k = 0; k < iterations; k++) {
                        String tool = TOOL_NAMES.get(k % TOOL_NAMES.size());
                        endpoint.reconnectMcpServer(endpoint.getMcpToolState().toolClientMap().get(tool), tool);
                    }
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "threads did not finish in time");

        assertTrue(errors.isEmpty(), "concurrent access threw: " + errors);

        // state consistency checks
        assertEquals(TOOL_NAMES.size(), endpoint.getMcpToolState().tools().size());
        assertEquals(TOOL_NAMES.size(), endpoint.getMcpToolState().toolClientMap().size());
        for (String name : TOOL_NAMES) {
            assertNotNull(endpoint.getMcpToolState().toolClientMap().get(name), "missing client for " + name);
        }
    }

    @Test
    void sameServerConcurrentReconnectReconnectsOnceAndClosesOldClientOnce() throws Exception {
        McpSyncClient initial = newMockClient();
        TestEndpoint endpoint = newEndpoint(initial);
        clientsCreated.set(0);

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<McpSyncClient> results = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    results.add(endpoint.reconnectMcpServer(initial, "get_weather"));
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "threads did not finish in time");

        assertTrue(errors.isEmpty(), "reconnect threw: " + errors);

        // exactly one real reconnect happened and the other threads observed it and skipped
        assertEquals(1, clientsCreated.get(), "server should have been reconnected exactly once");

        // the failed client was closed exactly once
        verify(initial, times(1)).closeGracefully();

        // every caller observed the same, non-null reconnected client
        McpSyncClient reconnected = endpoint.getMcpToolState().toolClientMap().get("get_weather");
        assertNotNull(reconnected);
        for (McpSyncClient c : results) {
            assertEquals(reconnected, c, "all callers should observe the same reconnected client");
        }
    }

    @Test
    void reconnectPrunesToolsThatVanishedServerSide() {
        serverTools = List.of("get_weather", "find_location");
        McpSyncClient initial = newMockClient();
        TestEndpoint endpoint = newEndpoint(initial);
        endpoint.addReturnDirectTool("find_location");

        // The server now only advertises 'get_weather', 'find_location' disappeared
        serverTools = List.of("get_weather");
        McpSyncClient reconnected = endpoint.reconnectMcpServer(initial, "get_weather");
        assertNotNull(reconnected);

        // 'find_location' must be pruned from every shared collection
        assertFalse(endpoint.getMcpToolState().toolClientMap().containsKey("find_location"), "stale entry in toolClientMap");
        assertFalse(endpoint.getMcpToolState().toolToServerName().containsKey("find_location"),
                "stale entry in toolToServerName");
        assertFalse(endpoint.getMcpToolState().returnDirectTools().contains("find_location"),
                "stale entry in returnDirectTools");
        assertFalse(endpoint.getMcpToolState().tools().stream().anyMatch(t -> t.function().name().equals("find_location")),
                "stale entry in cachedMcpTools");

        // 'get_weather' is still present and consistent across the collections
        assertTrue(endpoint.getMcpToolState().toolClientMap().containsKey("get_weather"));
        assertEquals(SERVER, endpoint.getMcpToolState().toolToServerName().get("get_weather"));
        assertEquals(1, endpoint.getMcpToolState().tools().size());
        assertEquals("get_weather", endpoint.getMcpToolState().tools().get(0).function().name());
    }
}
