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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolConfirmPopupTest {

    private static McpFacade.FileWrite request() {
        return new McpFacade.FileWrite(
                "demo.camel.yaml", Path.of("/tmp/demo"),
                "- route:\n    from:\n      uri: timer:tick\n      steps:\n        - log: hello\n",
                "- route:\n    from:\n      uri: timer:tick\n      steps:\n        - log:\n            message: hello\n"
                                                                                                    + "            logLevel: WARN\n",
                false, true);
    }

    @Test
    void enterAppliesAndReportsTheChangeSize() {
        ToolConfirmPopup popup = new ToolConfirmPopup();
        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        popup.open(request(), answer);

        assertTrue(popup.isVisible());
        assertEquals("+3 -1", popup.summaryForTesting());
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertFalse(popup.isVisible());
        assertTrue(answer.join());
    }

    @Test
    void theDiffIsOnlyAViewAndTheSummaryDecides() {
        ToolConfirmPopup popup = new ToolConfirmPopup();
        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        popup.open(request(), answer);

        // Esc in the diff returns to the summary; the write is still pending
        popup.handleKeyEvent(KeyEvent.ofChar('d'));
        assertTrue(popup.isDiffVisible());
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        assertFalse(popup.isDiffVisible());
        assertTrue(popup.isVisible());
        assertFalse(answer.isDone());

        // so does Enter, and d
        popup.handleKeyEvent(KeyEvent.ofChar('d'));
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertFalse(popup.isDiffVisible());
        assertFalse(answer.isDone());
        popup.handleKeyEvent(KeyEvent.ofChar('d'));
        popup.handleKeyEvent(KeyEvent.ofChar('d'));
        assertFalse(popup.isDiffVisible());
        assertFalse(answer.isDone());

        // the summary rejects with Esc
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        assertFalse(popup.isVisible());
        assertFalse(answer.join());
    }

    @Test
    void closingWithoutAnAnswerRejects() {
        ToolConfirmPopup popup = new ToolConfirmPopup();
        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        popup.open(new McpFacade.FileWrite("new.yaml", Path.of("/tmp/demo"), null, "- route: {}\n", true, false),
                answer);
        assertEquals("+1 -0", popup.summaryForTesting());

        popup.close();

        assertFalse(popup.isVisible());
        assertFalse(answer.join());
        assertFalse(popup.handleKeyEvent(KeyEvent.ofChar('d')), "a closed popup ignores keys");
    }

    @Test
    void aCallShowsWhatTheAiWantsToRunAndEnterRunsIt() {
        ToolConfirmPopup popup = new ToolConfirmPopup();
        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        popup.open(ToolConfirms.sendMessage("file:/home/me?fileName=.zshrc", List.of("kafka:orders")), answer);

        assertThat(render(popup, 100, 14))
                .contains("AI wants to send a message to an endpoint the integration does not have")
                .contains("file:/home/me?fileName=.zshrc")
                .contains("The message may leave the integration");
        // only a file has a diff
        popup.handleKeyEvent(KeyEvent.ofChar('d'));
        assertThat(popup.isDiffVisible()).isFalse();
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertThat(popup.isVisible()).isFalse();
        assertThat(answer.join()).isTrue();
    }

    @Test
    void aToolWaitsOnItsOwnThreadUntilTheUserAnswers() throws Exception {
        ToolConfirmPopup popup = new ToolConfirmPopup();
        CompletableFuture<Boolean> asked
                = CompletableFuture.supplyAsync(() -> popup.ask(ToolConfirms.sql("DROP TABLE orders")));

        await().atMost(5, TimeUnit.SECONDS).until(popup::isVisible);
        assertThat(render(popup, 100, 14)).contains("AI wants to run SQL that is not a SELECT")
                .contains("DROP TABLE orders");
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));

        assertThat(asked.get(5, TimeUnit.SECONDS)).isFalse();
    }

    private static String render(ToolConfirmPopup popup, int width, int height) {
        Rect area = new Rect(0, 0, width, height);
        Buffer buffer = Buffer.empty(area);
        popup.render(Frame.forTesting(buffer), area);
        return TuiTestHelper.bufferToString(buffer);
    }
}
