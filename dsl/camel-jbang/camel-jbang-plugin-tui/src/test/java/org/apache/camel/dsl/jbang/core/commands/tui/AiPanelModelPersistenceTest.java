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

import java.nio.file.Path;
import java.util.List;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The model chosen with /model is saved in the TUI settings, so it survives a restart.
 */
// it moves the home directory of the CLI, where other tests would look for theirs
@Isolated
class AiPanelModelPersistenceTest {

    @TempDir
    Path home;

    private Path previousHome;

    @BeforeEach
    void setup() {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
    }

    @AfterEach
    void restore() {
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void modelCommandPersistsSelectionAcrossRestarts() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(new ModelListingLlmClient(List.of("model-a", "model-b")));
        panel.open();

        for (char ch : "/model model-b".toCharArray()) {
            panel.handleKeyEvent(KeyEvent.ofChar(ch));
        }
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertThat(TuiSettings.load().getAiModel())
                .as("the /model selection must be persisted so it survives a TUI restart")
                .isEqualTo("model-b");
        assertThat(panel.conversationForTesting())
                .anyMatch(entry -> entry.text().contains("Switched model to model-b"));
    }

    private static final class ModelListingLlmClient extends LlmClient {

        private final List<String> models;

        ModelListingLlmClient(List<String> models) {
            this.models = models;
            withModel("test-model");
        }

        @Override
        public List<String> listModels() {
            return models;
        }
    }
}
