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

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

            @Override
            public void fallbackToOverviewIfTabInactive() {
            }
        };

        popupManager = new PopupManager(
                ctx, () -> List.of(info),
                () -> List.of(
                        new TabRegistry.MoreTab(TuiIcons.TAB_BEANS, "Beans", "&Beans", null),
                        new TabRegistry.MoreTab(TuiIcons.TAB_BROWSE, "Browse", "&Browse", null)),
                new FilesBrowser(), callbacks);
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
    void morePopupShortcutMatchesEitherCase() {
        // Both Beans and Browse use 'B' — pressing 'b'/'B' cycles between them (matchCount=2)
        // morePopupShortcut returns int[] {selectedIndex, matchCount}
        int[] bResult = popupManager.morePopupShortcut(KeyEvent.ofChar('b', KeyModifiers.NONE));
        assertEquals(0, bResult[0]);
        assertEquals(2, bResult[1]);
        // second press cycles to next match
        int[] bUpperResult = popupManager.morePopupShortcut(KeyEvent.ofChar('B', KeyModifiers.NONE));
        assertEquals(1, bUpperResult[0]);
        assertEquals(2, bUpperResult[1]);
        // 'z' matches nothing
        assertArrayEquals(new int[] { -1, 0 }, popupManager.morePopupShortcut(KeyEvent.ofChar('z', KeyModifiers.NONE)));
    }

    // ---- Confirm dialog key contract: Enter accepts, Esc cancels, anything else is swallowed ----

    @Test
    void confirmRunsCallbackOnEnter() {
        boolean[] ran = { false };
        popupManager.showConfirm("Confirm Quit", " Quit? ", () -> ran[0] = true);
        assertTrue(popupManager.isConfirmVisible());

        popupManager.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE), 0, 2);
        assertTrue(ran[0], "Enter must run the confirm callback");
        assertFalse(popupManager.isConfirmVisible(), "Confirm should close after Enter");
    }

    @Test
    void confirmCancelsOnEscapeWithoutRunningCallback() {
        boolean[] ran = { false };
        popupManager.showConfirm("Confirm Quit", " Quit? ", () -> ran[0] = true);

        popupManager.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE), 0, 2);
        assertFalse(ran[0], "Esc must not run the confirm callback");
        assertFalse(popupManager.isConfirmVisible(), "Confirm should close after Esc");
    }

    @Test
    void confirmIgnoresOtherKeys() {
        boolean[] ran = { false };
        popupManager.showConfirm("Confirm Quit", " Quit? ", () -> ran[0] = true);

        assertTrue(popupManager.handleKeyEvent(KeyEvent.ofChar('x'), 0, 2), "key is swallowed by the modal");
        assertFalse(ran[0], "a stray key must not confirm");
        assertTrue(popupManager.isConfirmVisible(), "a stray key must not dismiss the confirm");
    }

    @Test
    void killConfirmIgnoresOtherKeysAndCancelsOnEscape() {
        popupManager.showKillConfirm();

        popupManager.handleKeyEvent(KeyEvent.ofChar('x'), 0, 2);
        assertTrue(popupManager.isKillConfirmVisible(), "a stray key must not dismiss the kill confirm");

        popupManager.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE), 0, 2);
        assertFalse(popupManager.isKillConfirmVisible(), "Esc cancels the kill confirm");
    }
}
