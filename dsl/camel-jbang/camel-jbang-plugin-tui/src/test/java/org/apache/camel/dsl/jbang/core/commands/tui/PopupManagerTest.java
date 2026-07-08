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

import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
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

        popupManager = new PopupManager(
                ctx, () -> List.of(info),
                () -> List.of(
                        new TabRegistry.MoreTab(TuiIcons.TAB_BEANS, "Beans", "&Beans", null),
                        new TabRegistry.MoreTab(TuiIcons.TAB_BROWSE, "Browse", "Bro&wse", null)),
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
        // 'w'/'W' both select Browse (index 1); Shift+letter must work too
        assertEquals(1, popupManager.morePopupShortcut(KeyEvent.ofChar('w', KeyModifiers.NONE)));
        assertEquals(1, popupManager.morePopupShortcut(KeyEvent.ofChar('W', KeyModifiers.NONE)));
        assertEquals(0, popupManager.morePopupShortcut(KeyEvent.ofChar('B', KeyModifiers.NONE)));
        assertEquals(-1, popupManager.morePopupShortcut(KeyEvent.ofChar('z', KeyModifiers.NONE)));
    }
}
