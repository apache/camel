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

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

@Isolated
class AiPanelPromptHistoryTest {

    @Test
    void promptHistoryRecallWithUpDown(@TempDir Path tempDir) {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(LlmClient.create());
        panel.open();
        TuiPromptHistory history = TuiPromptHistory.load(10, tempDir.resolve("history.txt"));
        history.remember("first question");
        history.remember("second question");
        panel.setPromptHistoryForTesting(history);

        type(panel, "draft");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.UP, KeyModifiers.NONE));
        assertThat(panel.inputBufferForTesting()).isEqualTo("second question");

        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.UP, KeyModifiers.NONE));
        assertThat(panel.inputBufferForTesting()).isEqualTo("first question");

        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        assertThat(panel.inputBufferForTesting()).isEqualTo("second question");

        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        assertThat(panel.inputBufferForTesting()).isEqualTo("draft");
    }

    @Test
    void submitInputRemembersPrompt(@TempDir Path tempDir) {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(LlmClient.create());
        panel.open();
        panel.setPromptHistoryForTesting(TuiPromptHistory.load(10, tempDir.resolve("history.txt")));

        type(panel, "/help");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertThat(panel.promptHistoryEntriesForTesting()).containsExactly("/help");
    }

    @Test
    void promptHistoryDisabledWhenLimitZero(@TempDir Path tempDir) {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(LlmClient.create());
        panel.open();
        panel.setPromptHistoryForTesting(TuiPromptHistory.load(0, tempDir.resolve("history.txt")));

        type(panel, "/help");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        type(panel, "draft");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.UP, KeyModifiers.NONE));

        assertThat(panel.inputBufferForTesting()).isEqualTo("draft");
        assertThat(panel.promptHistoryEntriesForTesting()).isEmpty();
    }

    @Test
    void promptHistoryIgnoredInStatsView(@TempDir Path tempDir) {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(LlmClient.create());
        panel.open();
        TuiPromptHistory history = TuiPromptHistory.load(10, tempDir.resolve("history.txt"));
        history.remember("previous prompt");
        panel.setPromptHistoryForTesting(history);

        type(panel, "draft");
        panel.handleKeyEvent(McpFacade.parseKey("Ctrl+u"));
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.UP, KeyModifiers.NONE));

        assertThat(panel.inputBufferForTesting()).isEqualTo("draft");
    }

    private static void type(AiPanel panel, String text) {
        for (char ch : text.toCharArray()) {
            panel.handleKeyEvent(KeyEvent.ofChar(ch));
        }
    }
}
