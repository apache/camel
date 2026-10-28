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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * camel debug sends its commands (step, step over, skip over) in a file of their own, which another client of the
 * integration cannot overwrite, and the shared action file only to an integration before Camel 4.21.
 */
class DebugCommandTest {

    private static final long PID = 4242;

    @TempDir
    Path home;

    private Path previousHome;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicReference<String> requestFile = new AtomicReference<>();
    private final AtomicReference<JsonObject> request = new AtomicReference<>();
    private Thread integration;

    @BeforeEach
    void setup() throws Exception {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        Files.createDirectories(CommandLineHelper.getCamelDir());
    }

    @AfterEach
    void restore() throws Exception {
        running.set(false);
        if (integration != null) {
            integration.join(TimeUnit.SECONDS.toMillis(10));
        }
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void commandInAFileOfItsOwn() throws Exception {
        integration("4.24.0", true);

        assertThat(Debug.sendDebugCommand(PID, "stepover", null, 3)).isTrue();

        assertThat(requestFile.get()).startsWith(PID + "-action-").endsWith(".json");
        assertThat(request.get().getString("action")).isEqualTo("debug");
        assertThat(request.get().getString("command")).isEqualTo("stepover");
        assertThat(request.get().getInteger("position")).isEqualTo(3);
        // the answer is removed
        try (Stream<Path> files = Files.list(CommandLineHelper.getCamelDir())) {
            assertThat(files.map(f -> f.getFileName().toString())).noneMatch(n -> n.contains("-output"));
        }
    }

    @Test
    void sharedFileOfAnIntegrationBefore421() throws Exception {
        integration("4.20.0", false);

        // the integration takes the command without confirming it
        assertThat(Debug.sendDebugCommand(PID, "step", null, 0)).isTrue();

        assertThat(requestFile.get()).isEqualTo(PID + "-action.json");
        assertThat(request.get().getString("command")).isEqualTo("step");
    }

    /**
     * A fake integration with the given version of Camel that takes the next action, and confirms it when asked to (as
     * the connector does since Camel 4.24).
     */
    private void integration(String version, boolean confirms) throws IOException {
        Files.writeString(CommandLineHelper.getCamelDir().resolve(PID + "-status.json"),
                "{\"context\":{\"name\":\"orders\",\"version\":\"" + version + "\",\"phase\":5}}");
        integration = new Thread(() -> {
            Path dir = CommandLineHelper.getCamelDir();
            await().pollInterval(Duration.ofMillis(10)).atMost(30, TimeUnit.SECONDS)
                    .until(() -> !running.get() || next(dir).isPresent());
            next(dir).ifPresent(f -> take(f, confirms));
        }, "test-integration");
        integration.setDaemon(true);
        integration.start();
    }

    private static Optional<Path> next(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().startsWith(PID + "-action")).findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void take(Path file, boolean confirms) {
        try {
            request.set(Jsoner.deserialize(Files.readString(file), (JsonObject) null));
            requestFile.set(file.getFileName().toString());
            if (confirms) {
                Files.writeString(file.resolveSibling(file.getFileName().toString().replace("-action-", "-output-")),
                        "{\"status\":\"success\"}");
            }
            Files.delete(file);
        } catch (Exception e) {
            // the command withdrew the request
        }
    }
}
