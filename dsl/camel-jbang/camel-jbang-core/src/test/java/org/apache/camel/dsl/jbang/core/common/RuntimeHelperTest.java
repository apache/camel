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
package org.apache.camel.dsl.jbang.core.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeHelperTest {

    @TempDir
    Path home;
    private Path previousHome;
    private ExecutorService executor;

    @BeforeEach
    void setup() throws IOException {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        Files.createDirectories(CommandLineHelper.getCamelDir());
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void cleanup() throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void textApiPreservesRawResponseAndRequestConfiguration() throws Exception {
        var result = CompletableFuture.supplyAsync(() -> RuntimeHelper.executeAction(123, "route",
                request -> request.put("id", "route1"), 5000), executor);
        Path action = requestFile();
        JsonObject request = (JsonObject) Jsoner.deserialize(Files.readString(action));
        assertEquals("route", request.get("action"));
        assertEquals("route1", request.get("id"));
        String raw = "raw response\n  with whitespace\n";
        Files.writeString(outputFile(action), raw);
        assertEquals(raw, result.get(5, TimeUnit.SECONDS));
        assertNoRequestFiles();
    }

    @Test
    void textApiPreservesTimeoutDiagnostic() throws Exception {
        assertEquals("Timeout waiting for response from PID 123 for action: route",
                RuntimeHelper.executeAction(123, "route", null, 100));
        assertNoRequestFiles();
    }

    @Test
    void jsonApiWaitsForCompleteResponse() throws Exception {
        JsonObject request = new JsonObject();
        request.put("action", "semantic-metadata");
        var result = executor.submit(() -> RuntimeHelper.executeAction(123, request, 5000));
        Path action = requestFile();
        assertEquals(request, Jsoner.deserialize(Files.readString(action)));
        Path output = outputFile(action);
        Files.writeString(output, "{\"operations\":[");
        await().during(250, TimeUnit.MILLISECONDS).atMost(1, TimeUnit.SECONDS).until(() -> !result.isDone());
        Files.writeString(output, "{\"operations\":[]}");
        assertTrue(result.get(5, TimeUnit.SECONDS).getCollection("operations").isEmpty());
        assertNoRequestFiles();
    }

    @Test
    void interruptionCancelsRequestAndPreservesInterruptFlag() throws Exception {
        AtomicReference<Thread> worker = new AtomicReference<>();
        var result = executor.submit(() -> {
            worker.set(Thread.currentThread());
            JsonObject response = RuntimeHelper.executeAction(123, new JsonObject(), 5000);
            assertTrue(Thread.currentThread().isInterrupted());
            return response;
        });
        Path request = requestFile();
        worker.get().interrupt();
        assertNull(result.get(5, TimeUnit.SECONDS));
        assertFalse(Files.exists(request));
        assertNoRequestFiles();
    }

    @Test
    void jsonApiPropagatesPublicationFailureWhileTextApiKeepsItsDiagnostic() {
        CommandLineHelper.useHomeDir(home.resolve("missing").toString());
        assertThrows(IOException.class, () -> RuntimeHelper.executeAction(123, new JsonObject(), 100));
        assertEquals("Timeout waiting for response from PID 123 for action: route",
                RuntimeHelper.executeAction(123, "route", null, 100));
    }

    private Path requestFile() throws IOException {
        await().atMost(5, TimeUnit.SECONDS).until(() -> !requests().isEmpty());
        return requests().get(0);
    }

    private List<Path> requests() throws IOException {
        try (var files = Files.list(CommandLineHelper.getCamelDir())) {
            return files.filter(p -> p.getFileName().toString().startsWith("123-action-")).toList();
        }
    }

    private Path outputFile(Path action) {
        return action.resolveSibling(action.getFileName().toString().replace("-action-", "-output-"));
    }

    private void assertNoRequestFiles() throws IOException {
        try (var files = Files.list(CommandLineHelper.getCamelDir())) {
            assertEquals(0, files.count());
        }
    }
}
