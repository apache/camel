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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Isolated("Uses a temporary Camel home for real file IPC")
class MonitorContextRequestTest {
    @Test
    void lateResponseAfterTimeoutCannotSatisfyAnotherRequest(@TempDir Path directory) throws Exception {
        Path previous = CommandLineHelper.getHomeDir();
        var context = new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of()));
        CommandLineHelper.useHomeDir(directory.toString());
        try {
            Path camelDir = CommandLineHelper.getCamelDir();
            Files.createDirectories(camelDir);
            JsonObject request = new JsonObject();
            request.put("action", "semantic-evaluate");
            request.put("evaluation", "first");
            var first = CompletableFuture.supplyAsync(() -> context.executeIndependentAction("123", request, 500),
                    context.backgroundExecutor);
            AtomicReference<Path> firstAction = new AtomicReference<>();
            await().atMost(3, TimeUnit.SECONDS).until(() -> findAction(camelDir, firstAction));
            assertThat(first.get(3, TimeUnit.SECONDS)).isNull();
            assertThat(firstAction.get()).doesNotExist();

            request.put("evaluation", "second");
            var second = CompletableFuture.supplyAsync(() -> context.executeIndependentAction("123", request, 5000),
                    context.backgroundExecutor);
            AtomicReference<Path> secondAction = new AtomicReference<>();
            await().atMost(3, TimeUnit.SECONDS).until(() -> findAction(camelDir, secondAction));
            assertThat(secondAction.get()).isNotEqualTo(firstAction.get());
            Files.writeString(output(firstAction.get()), "{\"status\":\"success\",\"value\":\"first\"}");
            await().during(250, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS).until(() -> !second.isDone());
            Files.writeString(output(secondAction.get()), "{\"status\":\"success\",\"value\":\"second\"}");
            assertThat(second.get(3, TimeUnit.SECONDS)).containsEntry("value", "second");
            assertThat(secondAction.get()).doesNotExist();
            assertThat(output(secondAction.get())).doesNotExist();
        } finally {
            context.backgroundExecutor.shutdownNow();
            context.backgroundExecutor.awaitTermination(3, TimeUnit.SECONDS);
            CommandLineHelper.useHomeDir(previous.toString());
        }
    }

    @Test
    void waitsForCompleteJsonWhenResponseIsWrittenInParts(@TempDir Path directory) throws Exception {
        Path previous = CommandLineHelper.getHomeDir();
        var context = new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of()));
        CommandLineHelper.useHomeDir(directory.toString());
        try {
            Path camelDir = CommandLineHelper.getCamelDir();
            Files.createDirectories(camelDir);
            JsonObject request = new JsonObject();
            request.put("action", "semantic-metadata");
            var response = CompletableFuture.supplyAsync(() -> context.executeIndependentAction("123", request, 5000),
                    context.backgroundExecutor);
            AtomicReference<Path> action = new AtomicReference<>();
            await().atMost(3, TimeUnit.SECONDS).until(() -> findAction(camelDir, action));
            Path output = output(action.get());
            Files.writeString(output, "{\"operations\":[");
            await().during(250, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS).until(() -> !response.isDone());
            Files.writeString(output, "{\"name\":\"injection\"}]}", StandardOpenOption.APPEND);
            assertThat(SemanticTab.objects(response.get(3, TimeUnit.SECONDS), "operations"))
                    .singleElement().satisfies(operation -> assertThat(operation).containsEntry("name", "injection"));
            assertThat(output).doesNotExist();
            assertThat(action.get()).doesNotExist();
        } finally {
            context.backgroundExecutor.shutdownNow();
            context.backgroundExecutor.awaitTermination(3, TimeUnit.SECONDS);
            CommandLineHelper.useHomeDir(previous.toString());
        }
    }

    private static boolean findAction(Path directory, AtomicReference<Path> found) throws Exception {
        try (var paths = Files.list(directory)) {
            found.set(paths.filter(path -> path.getFileName().toString().startsWith("123-action-")).findFirst().orElse(null));
            return found.get() != null;
        }
    }

    private static Path output(Path action) {
        return action.resolveSibling(action.getFileName().toString().replace("-action-", "-output-"));
    }
}
