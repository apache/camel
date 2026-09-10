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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpPermissionPopupTest {

    private static List<JsonObject> options() {
        return List.of(
                AcpAgentClientTest.option("opt-allow", "Allow once", "allow_once"),
                AcpAgentClientTest.option("opt-always", "Always allow", "allow_always"),
                AcpAgentClientTest.option("opt-reject", "Reject", "reject_once"));
    }

    private static JsonObject toolCall() {
        JsonObject rawInput = new JsonObject();
        rawInput.put("file_path", "/tmp/route.yaml");
        JsonObject toolCall = new JsonObject();
        toolCall.put("title", "Write /tmp/route.yaml");
        toolCall.put("kind", "edit");
        toolCall.put("rawInput", rawInput);
        return toolCall;
    }

    @Test
    void rendersTitleKindInputAndOptions() {
        AcpPermissionPopup popup = new AcpPermissionPopup();
        popup.open(toolCall(), options());
        Rect area = new Rect(0, 0, 100, 30);
        Buffer buffer = Buffer.empty(area);
        popup.render(Frame.forTesting(buffer), area);
        String rendered = TuiTestHelper.bufferToString(buffer);
        assertTrue(rendered.contains("Write /tmp/route.yaml"));
        assertTrue(rendered.contains("edit"));
        assertTrue(rendered.contains("file_path"));
        assertTrue(rendered.contains("Allow once"));
        assertTrue(rendered.contains("Always allow"));
        assertTrue(rendered.contains("Reject"));
    }

    @Test
    void optionsStayVisibleOnAShortScreen() {
        AcpPermissionPopup popup = new AcpPermissionPopup();
        popup.open(toolCall(), options());
        Rect area = new Rect(0, 0, 80, 8);
        Buffer buffer = Buffer.empty(area);
        popup.render(Frame.forTesting(buffer), area);
        String rendered = TuiTestHelper.bufferToString(buffer);
        assertTrue(rendered.contains("Write /tmp/route.yaml"), rendered);
        assertTrue(rendered.contains("Allow once"), rendered);
        assertTrue(rendered.contains("Always allow"), rendered);
        assertTrue(rendered.contains("Reject"), rendered);
    }

    @Test
    void enterSelectsTheHighlightedOption() {
        AcpPermissionPopup popup = new AcpPermissionPopup();
        popup.open(toolCall(), options());
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        AcpPermissionPopup.Decision decision = popup.consumeDecision();
        assertNotNull(decision);
        assertEquals("opt-always", decision.optionId());
        assertFalse(popup.isVisible());
        assertNull(popup.consumeDecision(), "decision is consumed once");
    }

    @Test
    void escapePicksRejectOnceWhenOffered() {
        AcpPermissionPopup popup = new AcpPermissionPopup();
        popup.open(toolCall(), options());
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        assertEquals("opt-reject", popup.consumeDecision().optionId());
    }

    @Test
    void escapeAnswersCancelledWhenNoRejectOptionExists() {
        AcpPermissionPopup popup = new AcpPermissionPopup();
        popup.open(toolCall(), List.of(AcpAgentClientTest.option("opt-allow", "Allow", "allow_once")));
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        AcpPermissionPopup.Decision decision = popup.consumeDecision();
        assertNotNull(decision);
        assertNull(decision.optionId());
    }

    @Test
    void footerOffersSelectCancelTurnAndReject() {
        AcpPermissionPopup popup = new AcpPermissionPopup();
        popup.open(toolCall(), options());
        List<Span> spans = new ArrayList<>();
        popup.renderFooter(spans);
        String footer = spans.stream().map(Span::content).reduce("", String::concat);
        assertTrue(footer.contains("Enter"));
        assertTrue(footer.contains("Ctrl+C"));
        assertTrue(footer.contains("cancel turn"));
        assertTrue(footer.contains("Esc"));
    }

    @Test
    void clickOnAnOptionRowSelectsThatOption() {
        AcpPermissionPopup popup = new AcpPermissionPopup();
        popup.open(toolCall(), options());
        Rect area = new Rect(0, 0, 100, 30);
        Buffer buffer = Buffer.empty(area);
        popup.render(Frame.forTesting(buffer), area);
        Rect listRect = popup.listRectForTesting();
        popup.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, listRect.x() + 1, listRect.y() + 1));
        AcpPermissionPopup.Decision decision = popup.consumeDecision();
        assertNotNull(decision);
        assertEquals("opt-always", decision.optionId());
        assertFalse(popup.isVisible());
    }

    @Test
    void clickOutsideTheOptionRowsDoesNothing() {
        AcpPermissionPopup popup = new AcpPermissionPopup();
        popup.open(toolCall(), options());
        Rect area = new Rect(0, 0, 100, 30);
        Buffer buffer = Buffer.empty(area);
        popup.render(Frame.forTesting(buffer), area);
        Rect listRect = popup.listRectForTesting();
        popup.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, listRect.x(), listRect.y() - 2));
        assertNull(popup.consumeDecision());
        assertTrue(popup.isVisible());
    }
}
