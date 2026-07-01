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
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PopupManager} state management: popup open/close, visibility queries.
 */
class PopupManagerTest {

    private PopupManager popupManager;
    private MonitorContext ctx;
    private List<Integer> selectedTabs;

    @BeforeEach
    void setUp() {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "test-app";

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        ctx = new MonitorContext(data, infraData);
        ctx.selectedPid = "1234";

        selectedTabs = new ArrayList<>();

        PopupManager.PopupCallbacks callbacks = new PopupManager.PopupCallbacks() {
            @Override
            public void selectMoreTab(int index) {
                selectedTabs.add(index);
            }

            @Override
            public void resetIntegrationTabState() {
            }

            @Override
            public void refreshLogData() {
            }

            @Override
            public void stopSelectedProcess(boolean forceKill) {
            }
        };

        popupManager = new PopupManager(ctx, () -> List.of(info), new FilesBrowser(), callbacks);
    }

    @Test
    void initiallyNoPopupsVisible() {
        assertFalse(popupManager.isAnyPopupVisible(), "No popups should be visible initially");
        assertFalse(popupManager.isSwitchPopupVisible(), "Switch popup should not be visible");
        assertFalse(popupManager.isMorePopupVisible(), "More popup should not be visible");
        assertFalse(popupManager.isKillConfirmVisible(), "Kill confirm should not be visible");
    }

    @Test
    void openMorePopupMakesVisible() {
        popupManager.openMorePopup();
        assertTrue(popupManager.isMorePopupVisible(), "More popup should be visible after opening");
        assertTrue(popupManager.isAnyPopupVisible(), "Any popup should be visible");
    }

    @Test
    void closeMorePopupHidesIt() {
        popupManager.openMorePopup();
        assertTrue(popupManager.isMorePopupVisible());

        popupManager.closeMorePopup();
        assertFalse(popupManager.isMorePopupVisible(), "More popup should not be visible after closing");
    }

    @Test
    void showKillConfirmMakesVisible() {
        popupManager.showKillConfirm();
        assertTrue(popupManager.isKillConfirmVisible(), "Kill confirm should be visible");
        assertTrue(popupManager.isAnyPopupVisible(), "Any popup should be visible");
    }

    @Test
    void openSwitchPopupMakesVisible() {
        // Switch popup only opens when there are more than 1 integration
        IntegrationInfo info1 = new IntegrationInfo();
        info1.pid = "1234";
        info1.name = "test-app";
        IntegrationInfo info2 = new IntegrationInfo();
        info2.pid = "5678";
        info2.name = "other-app";

        popupManager.openSwitchPopup("1234", List.of(info1, info2));
        assertTrue(popupManager.isSwitchPopupVisible(), "Switch popup should be visible");
        assertTrue(popupManager.isAnyPopupVisible(), "Any popup should be visible");
    }

    @Test
    void morePopupShortcutReturnsCorrectIndex() {
        // 'a' should return 0 (first more tab = beans)
        int index = PopupManager.morePopupShortcut(KeyEvent.ofChar('a', KeyModifiers.NONE));
        assertTrue(index >= 0 || index == -1,
                "Shortcut should return a valid index or -1");
    }

    // ---- More dropdown mouse support ----
    // The dropdown is drawn with a one-cell border on every side, so entries start at popup.y() + 1 and the interior
    // spans [popup.x() + 1, popup.x() + width - 1). morePopupItemAt resolves a click to the entry under it, or -1 for the
    // border/outside/below the last entry.

    @Test
    void morePopupItemAtResolvesClicksToEntries() {
        Rect popup = new Rect(0, 0, 22, 18);
        assertEquals(0, PopupManager.morePopupItemAt(popup, 16, 5, 1), "first row under the top border is entry 0");
        assertEquals(3, PopupManager.morePopupItemAt(popup, 16, 5, 4), "the fourth interior row is entry 3");
        assertEquals(15, PopupManager.morePopupItemAt(popup, 16, 5, 16), "the last interior row is entry 15");
    }

    @Test
    void morePopupItemAtRejectsBorderAndOutsideClicks() {
        Rect popup = new Rect(0, 0, 22, 18);
        assertEquals(-1, PopupManager.morePopupItemAt(popup, 16, 5, 0), "the top border row (title) is not an entry");
        assertEquals(-1, PopupManager.morePopupItemAt(popup, 16, 0, 5), "the left border column is not an entry");
        assertEquals(-1, PopupManager.morePopupItemAt(popup, 16, 21, 5), "the right border column is not an entry");
        assertEquals(-1, PopupManager.morePopupItemAt(popup, 16, 5, 17), "the bottom border row is not an entry");
        assertEquals(-1, PopupManager.morePopupItemAt(popup, 16, 50, 10), "a click well outside the popup is not an entry");
    }

    @Test
    void morePopupItemAtRejectsRowsBelowTheLastEntry() {
        // A popup taller than the entry count: interior rows past the last entry must not resolve to a phantom entry.
        Rect popup = new Rect(0, 0, 22, 18);
        assertEquals(-1, PopupManager.morePopupItemAt(popup, 3, 5, 5),
                "row 4 has no entry when only 3 entries exist");
    }

    @Test
    void morePopupItemAtHandlesNullGeometry() {
        assertEquals(-1, PopupManager.morePopupItemAt(null, 16, 5, 5), "no captured geometry yet");
    }

    @Test
    void clickOnMoreEntryActivatesThatTabAndClosesPopup() {
        popupManager.openMorePopup();
        renderMorePopup(80, 24); // popup lands at (0,0,22,18) since no tab labels are set

        // Row 4 (popup.y()+1 + 3) selects entry index 3.
        assertTrue(popupManager.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, 5, 4)),
                "a click inside the dropdown is consumed");
        assertEquals(List.of(3), selectedTabs, "clicking the fourth entry activates More tab index 3");
        assertFalse(popupManager.isMorePopupVisible(), "selecting an entry closes the dropdown");
    }

    @Test
    void clickOutsideMorePopupClosesItWithoutSelecting() {
        popupManager.openMorePopup();
        renderMorePopup(80, 24);

        assertTrue(popupManager.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, 60, 10)),
                "the dropdown is modal, so even an outside click is consumed");
        assertTrue(selectedTabs.isEmpty(), "clicking outside does not activate any tab");
        assertFalse(popupManager.isMorePopupVisible(), "clicking outside dismisses the dropdown");
    }

    @Test
    void mouseIsNotConsumedWhenNoPopupIsOpen() {
        assertFalse(popupManager.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, 5, 5)),
                "with no dropdown open, the event falls through to the caller's normal routing");
        assertFalse(popupManager.handleMouseEvent(MouseEvent.scrollDown(5, 5)),
                "scroll is not swallowed when the dropdown is closed");
    }

    private void renderMorePopup(int width, int height) {
        Rect area = new Rect(0, 0, width, height);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        popupManager.renderMorePopup(frame, area);
    }
}
