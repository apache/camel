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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * An integration for the tests of the tools: it answers the actions the CLI writes in the Camel directory, as the CLI
 * connector does, with what the test gives for the action, and keeps the actions it took.
 */
final class FakeIntegration implements AutoCloseable {

    private final long pid;
    private final Function<JsonObject, Object> answers;
    private final List<JsonObject> actions = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread thread;

    /**
     * @param pid     the pid of the integration, which the tools select
     * @param answers the answer to an action (as the CLI connector writes it), or null for none
     */
    FakeIntegration(long pid, Function<JsonObject, Object> answers) {
        this.pid = pid;
        this.answers = answers;
        thread = new Thread(this::answerActions, "test-integration");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * The actions the integration took, in order.
     */
    List<JsonObject> actions() {
        return actions;
    }

    /**
     * The last action the integration took.
     */
    JsonObject lastAction() {
        return actions.isEmpty() ? null : actions.get(actions.size() - 1);
    }

    @Override
    public void close() throws InterruptedException {
        running.set(false);
        thread.join(5000);
    }

    private void answerActions() {
        Path dir = CommandLineHelper.getCamelDir();
        while (running.get()) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path action : files.filter(f -> f.getFileName().toString().startsWith(pid + "-action-")).toList()) {
                    String text = Files.readString(action);
                    if (text.isBlank()) {
                        continue;
                    }
                    JsonObject root = (JsonObject) Jsoner.deserialize(text);
                    actions.add(root);
                    Object answer = answers.apply(root);
                    if (answer != null) {
                        Path output = dir.resolve(action.getFileName().toString().replace("-action-", "-output-"));
                        Path temporary = dir.resolve(pid + "-answer.tmp");
                        Files.writeString(temporary, answer instanceof String s ? s : Jsoner.serialize(answer));
                        Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
                    }
                    Files.deleteIfExists(action);
                }
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException | DeserializationException | RuntimeException e) {
                // a half written action: the next look reads it
            }
        }
    }
}
