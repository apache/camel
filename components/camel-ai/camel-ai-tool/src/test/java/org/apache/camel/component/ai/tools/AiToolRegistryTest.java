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
package org.apache.camel.component.ai.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiToolRegistryTest {

    private AiToolRegistry registry;

    @BeforeEach
    public void setUp() {
        registry = new AiToolRegistry();
    }

    @Test
    public void testPutAndGetTool() {
        AiToolSpec spec = new AiToolSpec("getTool", "A test tool", Map.of(), null, null);

        registry.put("weather", spec);

        assertThat(registry.getTools().get("weather"))
                .as("Tools registered under 'weather' tag")
                .isNotNull()
                .hasSize(1)
                .contains(spec);
    }

    @Test
    public void testRemoveTool() {
        AiToolSpec spec = new AiToolSpec("getTool", "A test tool", Map.of(), null, null);

        registry.put("weather", spec);
        assertThat(registry.getTools().get("weather"))
                .as("Tool should be registered before removal")
                .hasSize(1);

        registry.remove("weather", spec);
        assertThat(registry.getTools().get("weather"))
                .as("Tag entry should be removed when last tool is removed")
                .isNull();
    }

    @Test
    public void testMultipleToolsWithSameTag() {
        AiToolSpec spec1 = new AiToolSpec("tool1", "Tool 1", Map.of(), null, null);
        AiToolSpec spec2 = new AiToolSpec("tool2", "Tool 2", Map.of(), null, null);

        registry.put("assistant", spec1);
        registry.put("assistant", spec2);

        assertThat(registry.getTools().get("assistant"))
                .as("Both tools should be registered under 'assistant' tag")
                .hasSize(2);
    }

    @Test
    public void testRemoveOneOfMultipleTools() {
        AiToolSpec spec1 = new AiToolSpec("tool1", "Tool 1", Map.of(), null, null);
        AiToolSpec spec2 = new AiToolSpec("tool2", "Tool 2", Map.of(), null, null);

        registry.put("assistant", spec1);
        registry.put("assistant", spec2);

        registry.remove("assistant", spec1);

        assertThat(registry.getTools().get("assistant"))
                .as("Only the non-removed tool should remain")
                .hasSize(1)
                .contains(spec2)
                .doesNotContain(spec1);
    }

    @Test
    public void testTagIsolation() {
        AiToolSpec weatherSpec = new AiToolSpec("weather", "Weather", Map.of(), null, null);
        AiToolSpec emailSpec = new AiToolSpec("email", "Email", Map.of(), null, null);

        registry.put("weather", weatherSpec);
        registry.put("email", emailSpec);

        assertThat(registry.getTools().get("weather"))
                .as("Weather tag should only contain the weather tool")
                .hasSize(1)
                .contains(weatherSpec);

        assertThat(registry.getTools().get("email"))
                .as("Email tag should only contain the email tool")
                .hasSize(1)
                .contains(emailSpec);
    }

    @Test
    public void testRemoveFromNonExistentTag() {
        AiToolSpec spec = new AiToolSpec("tool", "A tool", Map.of(), null, null);
        registry.remove("nonexistent", spec);

        assertThat(registry.getTools())
                .as("Registry should remain empty after removing from non-existent tag")
                .isEmpty();
        assertThat(registry.getAllTools())
                .as("All tools should remain empty")
                .isEmpty();
    }

    @Test
    public void testDefaultPoolPutAndRemove() {
        AiToolSpec spec = new AiToolSpec("defaultTool", "Default", Map.of(), null, null);

        registry.putDefault(spec);
        assertThat(registry.getDefaultTools())
                .as("Default pool should contain the tool")
                .hasSize(1)
                .contains(spec);

        registry.removeDefault(spec);
        assertThat(registry.getDefaultTools())
                .as("Default pool should be empty after removal")
                .isEmpty();
    }

    @Test
    public void testGetToolsByTagIncludesDefaultPool() {
        AiToolSpec taggedTool = new AiToolSpec("tagged", "Tagged", Map.of(), null, null);
        AiToolSpec defaultTool = new AiToolSpec("default", "Default", Map.of(), null, null);

        registry.put("weather", taggedTool);
        registry.putDefault(defaultTool);

        assertThat(registry.getToolsByTag("weather"))
                .as("Tools by tag should include both tagged and default pool tools")
                .hasSize(2)
                .contains(taggedTool)
                .contains(defaultTool);
    }

    @Test
    public void testGetToolsByTagWithNoMatchReturnsDefaultOnly() {
        AiToolSpec defaultTool = new AiToolSpec("default", "Default", Map.of(), null, null);
        registry.putDefault(defaultTool);

        assertThat(registry.getToolsByTag("nonexistent"))
                .as("Non-existent tag should return only default pool tools")
                .hasSize(1)
                .contains(defaultTool);
    }

    @Test
    public void testConcurrentPutRemoveAndGet() throws Exception {
        int threadCount = 10;
        int opsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < opsPerThread; i++) {
                    AiToolSpec spec = new AiToolSpec(
                            "tool-" + threadId + "-" + i, "desc", Map.of(), null, null);
                    registry.put("concurrent", spec);
                    registry.getToolsByTag("concurrent");
                    registry.getAllTools();
                    registry.remove("concurrent", spec);
                }
            }));
        }

        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(registry.getToolsByTag("concurrent"))
                .as("All tools should be removed after concurrent ops")
                .isEmpty();
    }

    @Test
    public void testGetAllToolsMergesTaggedAndDefault() {
        AiToolSpec tool1 = new AiToolSpec("tool1", "Tool 1", Map.of(), null, null);
        AiToolSpec tool2 = new AiToolSpec("tool2", "Tool 2", Map.of(), null, null);
        AiToolSpec defaultTool = new AiToolSpec("default", "Default", Map.of(), null, null);

        registry.put("weather", tool1);
        registry.put("email", tool2);
        registry.putDefault(defaultTool);

        Set<AiToolSpec> all = registry.getAllTools();
        assertThat(all)
                .as("All tools should include tagged and default tools")
                .hasSize(3)
                .contains(tool1, tool2, defaultTool);
    }
}
