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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SemanticImageViewTest {
    @Test
    void imageDecodingIsDeferredAndRenderingUsesBoundedColoredPixels() throws Exception {
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xff0000);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        var input = Map.of("$semanticInput", List.of(Map.of("type", "image", "data",
                "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray()))));
        List<Runnable> work = new ArrayList<>();
        var view = new SemanticImageView(work::add);
        assertThat(view.lines(input, 2)).hasSize(1);
        assertThat(work).hasSize(1);
        work.get(0).run();
        var lines = view.lines(input, 2);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(1).spans()).hasSize(2);
        assertThat(lines.get(1).spans().get(0).content()).isEqualTo("▀");
        assertThat(work).hasSize(1);
        view.reset();
        view.lines(input, 2);
        assertThat(work).hasSize(2);
    }

    @Test
    void capturedImageDraftKeepsPayloadOutOfTheEditorAndSendsItOnlyOnRun() {
        var runtime = new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of())) {
            final List<JsonObject> calls = new CopyOnWriteArrayList<>();

            @Override
            JsonObject executeIndependentAction(String pid, JsonObject request, long timeout) {
                calls.add(request);
                return new JsonObject(Map.of("status", "success", "value", true));
            }
        };
        try {
            var integration = new IntegrationInfo();
            integration.pid = "image";
            runtime.data.set(List.of(integration));
            runtime.selectedPid = "image";
            var operation = new JsonObject(
                    Map.of("name", "boolean", "contract", new JsonObject(
                            Map.of("inputTypes", List.of("text", "image"), "maxImageBytes", 10_000_000, "parameters",
                                    List.of()))));
            var playground = new SemanticPlayground(runtime, "image", "expert", operation);
            var input = Map.of("$semanticInput", List.of(Map.of("type", "image", "data", "data:image/png;base64,AQID")));
            playground.loadAuditInput(new JsonObject(Map.of("eventId", "record", "input", input)));
            assertThat(playground.snapshot().toJson()).doesNotContain("base64", "AQID");
            assertThat(runtime.calls).isEmpty();
            playground.handleKeyEvent(KeyEvent.ofChar('r', KeyModifiers.CTRL));
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(runtime.calls).hasSize(1));
            assertThat(runtime.calls.get(0).get("input")).isEqualTo(input);
            assertThat(playground.snapshot().toJson()).doesNotContain("base64", "AQID");
        } finally {
            runtime.backgroundExecutor.shutdownNow();
        }
    }

    @Test
    void capturedTextIsNeverInterpretedAsALocalImagePath(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("captured.png");
        Files.write(file, new byte[] { 1, 2, 3 });
        var runtime = new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of())) {
            final List<JsonObject> calls = new CopyOnWriteArrayList<>();

            @Override
            JsonObject executeIndependentAction(String pid, JsonObject request, long timeout) {
                calls.add(request);
                return new JsonObject(Map.of("status", "success", "value", true));
            }
        };
        try {
            var integration = new IntegrationInfo();
            integration.pid = "image";
            runtime.data.set(List.of(integration));
            runtime.selectedPid = "image";
            var operation = new JsonObject(
                    Map.of("name", "boolean", "contract", new JsonObject(
                            Map.of("inputTypes", List.of("text", "image"), "maxImageBytes", 10_000_000, "parameters",
                                    List.of()))));
            var playground = new SemanticPlayground(runtime, "image", "expert", operation);
            String input = "image:" + file;
            playground.loadAuditInput(new JsonObject(Map.of("eventId", "record", "input", input)));
            assertThat(runtime.calls).isEmpty();
            playground.handleKeyEvent(KeyEvent.ofChar('r', KeyModifiers.CTRL));
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(runtime.calls).hasSize(1));
            assertThat(runtime.calls.get(0).get("input")).isEqualTo(input);
            var typed = new SemanticPlayground(runtime, "image", "expert", operation);
            assertThat(typed.setInputValue("input", input)).isTrue();
            typed.handleKeyEvent(KeyEvent.ofChar('r', KeyModifiers.CTRL));
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(runtime.calls).hasSize(2));
            assertThat(runtime.calls.get(1).get("input")).isEqualTo(
                    Map.of("$semanticInput", List.of(Map.of("type", "image", "data", "data:image/png;base64,AQID"))));
        } finally {
            runtime.backgroundExecutor.shutdownNow();
        }
    }

    @Test
    void expertLimitRejectsLocalAndInlineImagesBeforeSubmitting(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("large.png");
        Files.write(file, new byte[] { 1, 2, 3 });
        var runtime = new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of())) {
            final List<JsonObject> calls = new CopyOnWriteArrayList<>();

            @Override
            JsonObject executeIndependentAction(String pid, JsonObject request, long timeout) {
                calls.add(request);
                return new JsonObject(Map.of("status", "success"));
            }
        };
        try {
            var integration = new IntegrationInfo();
            integration.pid = "image";
            runtime.data.set(List.of(integration));
            runtime.selectedPid = "image";
            var operation = new JsonObject(
                    Map.of("name", "boolean", "contract", new JsonObject(
                            Map.of("inputTypes", List.of("text", "image"), "parameters", List.of(), "maxImageBytes", 2))));
            for (String input : List.of("image:" + file, "data:image/png;base64,AQID")) {
                var playground = new SemanticPlayground(runtime, "image", "expert", operation);
                assertThat(playground.setInputValue("input", input)).isTrue();
                playground.handleKeyEvent(KeyEvent.ofChar('r', KeyModifiers.CTRL));
                await().atMost(5, TimeUnit.SECONDS).untilAsserted(
                        () -> assertThat(playground.snapshot().toJson()).contains("expert maxImageBytes limit of 2 bytes"));
                assertThat(runtime.calls).isEmpty();
            }
            for (String body : List.of("image:" + file, "data:image/png;base64,AQID", "AQID")) {
                var popup = new SemanticEvaluationPopup(runtime, "image", new JsonObject(Map.of("name", "inspect")), operation);
                assertThat(popup.setInputValue("sample", new JsonObject(
                        Map.of("body", body, "headers", Map.of("Content-Type", "image/png"))).toJson())).isTrue();
                popup.handleKeyEvent(KeyEvent.ofChar('r', KeyModifiers.CTRL));
                await().atMost(5, TimeUnit.SECONDS).untilAsserted(
                        () -> assertThat(popup.snapshot().toJson()).contains("expert maxImageBytes limit of 2 bytes"));
                assertThat(runtime.calls).isEmpty();
            }
        } finally {
            runtime.backgroundExecutor.shutdownNow();
        }
    }
}
