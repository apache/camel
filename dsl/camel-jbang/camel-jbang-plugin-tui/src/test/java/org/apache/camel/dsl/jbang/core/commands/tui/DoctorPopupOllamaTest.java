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

import java.util.ArrayList;
import java.util.List;

import dev.tamboui.text.Line;
import org.apache.camel.dsl.jbang.core.common.OllamaDoctorSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DoctorPopupOllamaTest {

    @Test
    void aiProviderRecognizesRunningOllamaWithoutCloudKeys() {
        List<Line> lines = new ArrayList<>();
        OllamaDoctorSupport.Status ollama
                = new OllamaDoctorSupport.Status(true, "http://localhost:11434", List.of("qwen2.5:32b"));

        DoctorPopup.addAiProviderLines(lines, null, ollama);

        assertThat(lineTexts(lines)).anyMatch(text -> text.contains("Ollama (local)"));
    }

    @Test
    void aiProviderPrefersCloudKeyOverOllama() {
        List<Line> lines = new ArrayList<>();
        OllamaDoctorSupport.Status ollama
                = new OllamaDoctorSupport.Status(true, "http://localhost:11434", List.of("llama3.2:latest"));

        DoctorPopup.addAiProviderLines(lines, "OpenAI", ollama);

        assertThat(lineTexts(lines)).anyMatch(text -> text.contains("OpenAI"));
        assertThat(lineTexts(lines)).noneMatch(text -> text.contains("Ollama (local)"));
    }

    @Test
    void aiProviderWarnsWhenNoCloudKeyAndOllamaDown() {
        List<Line> lines = new ArrayList<>();

        DoctorPopup.addAiProviderLines(lines, null, OllamaDoctorSupport.Status.notRunning());

        assertThat(lineTexts(lines)).anyMatch(text -> text.contains("No API key configured"));
        assertThat(lineTexts(lines)).anyMatch(text -> text.contains("start Ollama"));
    }

    @Test
    void ollamaRowShowsModelsWhenRunning() {
        List<Line> lines = new ArrayList<>();
        OllamaDoctorSupport.Status status = new OllamaDoctorSupport.Status(
                true, "http://localhost:11434", List.of("qwen2.5:32b", "llama3.2:latest"));

        DoctorPopup.addOllamaLines(lines, status);

        assertThat(lineTexts(lines)).anyMatch(text -> text.contains("2 models"));
        assertThat(lineTexts(lines)).anyMatch(text -> text.contains("models: qwen2.5:32b, llama3.2:latest"));
    }

    @Test
    void aiProviderWarnsWhenOllamaRunningWithoutModels() {
        List<Line> lines = new ArrayList<>();
        OllamaDoctorSupport.Status ollama = new OllamaDoctorSupport.Status(true, "http://localhost:11434", List.of());

        DoctorPopup.addAiProviderLines(lines, null, ollama);

        assertThat(lineTexts(lines)).anyMatch(text -> text.contains("Ollama (no models)"));
        assertThat(lineTexts(lines)).anyMatch(text -> text.contains("ollama pull"));
    }

    @Test
    void ollamaRowTruncatesLongModelList() {
        List<Line> lines = new ArrayList<>();
        OllamaDoctorSupport.Status status = new OllamaDoctorSupport.Status(
                true,
                "http://localhost:11434",
                List.of("model-one-with-a-long-name", "model-two-with-a-long-name", "model-three"));

        DoctorPopup.addOllamaLines(lines, status);

        assertThat(lineTexts(lines)).anyMatch(text -> text.contains("models:") && text.contains("..."));
    }

    @Test
    void ollamaRowWarnsWhenNotRunning() {
        List<Line> lines = new ArrayList<>();

        DoctorPopup.addOllamaLines(lines, OllamaDoctorSupport.Status.notRunning());

        assertThat(lineTexts(lines)).anyMatch(text -> text.contains("Not detected"));
        assertThat(lineTexts(lines)).anyMatch(text -> text.contains("ollama serve"));
    }

    private static List<String> lineTexts(List<Line> lines) {
        return lines.stream().map(Object::toString).toList();
    }
}
