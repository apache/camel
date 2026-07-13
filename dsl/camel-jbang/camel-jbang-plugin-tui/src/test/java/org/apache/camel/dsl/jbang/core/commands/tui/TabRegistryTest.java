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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.CharWidth;
import dev.tamboui.widgets.tabs.TabsState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link TabRegistry} constants, tab index mapping, and the {@link TabRegistry.MoreTab} records that are the
 * single source of truth for the More submenu (icon, name, mnemonic label, tab instance).
 */
class TabRegistryTest {

    private TabRegistry registry;

    @BeforeEach
    void setUp() {
        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of());
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        MonitorContext ctx = new MonitorContext(data, infraData);
        DataRefreshService dataService = new DataRefreshService(
                "test",
                new DataRefreshService.RefreshContext() {
                    @Override
                    public int selectedTab() {
                        return 0;
                    }

                    @Override
                    public boolean isSwitchPopupVisible() {
                        return false;
                    }

                    @Override
                    public String getPendingAutoSelect() {
                        return null;
                    }

                    @Override
                    public void clearPendingAutoSelect() {
                    }

                    @Override
                    public void onInfraAutoSelected(int tableIndex, String pid) {
                    }

                    @Override
                    public boolean isInfraSelected() {
                        return false;
                    }
                },
                Path::of, Path::of);
        registry = new TabRegistry(new TabsState(TabRegistry.TAB_OVERVIEW));
        registry.initTabs(ctx, dataService, () -> {
        });
    }

    @Test
    void moreTabsHasTwentyOneEntries() {
        assertEquals(21, registry.moreTabs().size());
    }

    @Test
    void everyMoreTabLabelHasMnemonicMarker() {
        for (TabRegistry.MoreTab mt : registry.moreTabs()) {
            assertTrue(mt.mnemonicIndex() >= 0,
                    "More tab '" + mt.name() + "' label must carry a '" + TuiIcons.MNEMONIC_MARKER + "' marker: "
                                                + mt.label());
        }
    }

    @Test
    void moreTabIconsAreTwoColumnsWideWithoutVariationSelector() {
        for (TabRegistry.MoreTab mt : registry.moreTabs()) {
            assertEquals(2, CharWidth.of(mt.icon()), "Icon should be 2 terminal columns wide: " + mt.icon());
            assertFalse(mt.icon().contains("\uFE0F"), "Icon should not contain VS16 variation selector: " + mt.icon());
        }
    }

    @Test
    void moreTabShortcutsMatchHistoricalSequence() {
        // Independent oracle (not a re-derivation of shortcut()): these are the exact letters the hand-maintained
        // MORE_SHORTCUTS array carried before the MoreTab refactor. A label edit that repoints a key must fail here.
        List<Character> shortcuts = registry.moreTabs().stream().map(TabRegistry.MoreTab::shortcut).toList();
        assertEquals(
                List.of('B', 'W', 'C', 'A', 'G', 'N', 'V', 'L', 'H', 'I', 'J', 'D', 'M', 'K', 'E', 'Q', 'R', 'O', 'P', 'S',
                        'T'),
                shortcuts, "More tab shortcut letters must match the historical sequence");
    }

    @Test
    void moreTabShortcutsAreUnique() {
        // morePopupShortcut() returns the first matching tab, so a duplicated letter would silently shadow a later tab.
        List<Character> shortcuts = registry.moreTabs().stream().map(TabRegistry.MoreTab::shortcut).toList();
        assertEquals(shortcuts.size(), Set.copyOf(shortcuts).size(),
                "More tab shortcut letters must be unique: " + shortcuts);
    }

    @Test
    void moreTabRejectsLabelWithoutUsableMnemonicMarker() {
        // The record's compact constructor enforces the '&'-before-a-letter invariant that shortcut() relies on,
        // so a mistyped literal fails loudly at construction instead of throwing StringIndexOutOfBounds later.
        assertThrows(IllegalArgumentException.class,
                () -> new TabRegistry.MoreTab(TuiIcons.TAB_BEANS, "Beans", "Beans", null));
        assertThrows(IllegalArgumentException.class,
                () -> new TabRegistry.MoreTab(TuiIcons.TAB_BEANS, "Beans", "Beans&", null));
    }

    @Test
    void moreTabIndexResolvesSqlQueryByNameForEditSqlJump() {
        // Guards the selectMoreTab(moreTabIndex("SQL Query")) wiring that replaced a hardcoded index.
        int idx = registry.moreTabIndex("SQL Query");
        assertEquals("SQL Query", registry.moreTabs().get(idx).name());
        assertEquals(-1, registry.moreTabIndex("Nonexistent"), "Unknown name resolves to -1");
    }

    @Test
    void allTabEntriesExposeDigitsIconsAndMoreShortcuts() {
        List<TabRegistry.TabEntry> entries = registry.allTabEntries();
        assertEquals(9 + registry.moreTabs().size(), entries.size());

        // Primary tabs: digit shortcuts 1-9, moreIndex -1, icon indexed by tabIndex.
        for (int i = 0; i < 9; i++) {
            TabRegistry.TabEntry e = entries.get(i);
            assertEquals(String.valueOf(i + 1), e.shortcut(), "Primary tab " + e.name() + " digit shortcut");
            assertEquals(-1, e.moreIndex(), "Primary tab " + e.name() + " has no More index");
            assertEquals(TuiIcons.PRIMARY_TAB_ICONS.get(e.tabIndex()), e.icon());
        }

        // More tabs: tabIndex TAB_MORE, ascending moreIndex, shortcut/name/icon carried from the owning MoreTab.
        for (int i = 0; i < registry.moreTabs().size(); i++) {
            TabRegistry.TabEntry e = entries.get(9 + i);
            TabRegistry.MoreTab mt = registry.moreTabs().get(i);
            assertEquals(TabRegistry.TAB_MORE, e.tabIndex());
            assertEquals(i, e.moreIndex());
            assertEquals(String.valueOf(mt.shortcut()), e.shortcut());
            assertEquals(mt.name(), e.name());
            assertEquals(mt.icon(), e.icon());
        }
    }

    @Test
    void browseAndConfigurationHighlightTheirShortcutLetter() {
        // Regression guard: these two historically underlined the wrong letter when a hand-maintained index array
        // drifted. The '&' marker now pins the highlight to the actual shortcut.
        TabRegistry.MoreTab browse = moreTabNamed("Browse");
        assertEquals(3, browse.mnemonicIndex(), "Should underline the 'w' in Bro[w]se");
        assertEquals('W', browse.shortcut());

        TabRegistry.MoreTab configuration = moreTabNamed("Configuration");
        assertEquals(5, configuration.mnemonicIndex(), "Should underline the 'g' in Confi[g]uration");
        assertEquals('G', configuration.shortcut());
    }

    @Test
    void spansTabKeepsProgrammaticNameButOTelPopupLabel() {
        TabRegistry.MoreTab spans = moreTabNamed("Spans");
        assertEquals("Spans", spans.name(), "MCP/Go-to lookup name must stay 'Spans'");
        assertEquals("OTel Spans", spans.displayName(), "Popup shows the OTel-qualified label");
        assertEquals('O', spans.shortcut());
    }

    private TabRegistry.MoreTab moreTabNamed(String name) {
        return registry.moreTabs().stream()
                .filter(mt -> mt.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No More tab named " + name));
    }

    @Test
    void tabConstantsAreSequential() {
        assertEquals(0, TabRegistry.TAB_OVERVIEW, "TAB_OVERVIEW should be 0");
        assertEquals(1, TabRegistry.TAB_LOG, "TAB_LOG should be 1");
        assertEquals(2, TabRegistry.TAB_ACTIVITY, "TAB_ACTIVITY should be 2");
        assertEquals(3, TabRegistry.TAB_DIAGRAM, "TAB_DIAGRAM should be 3");
        assertEquals(4, TabRegistry.TAB_ROUTES, "TAB_ROUTES should be 4");
        assertEquals(5, TabRegistry.TAB_ENDPOINTS, "TAB_ENDPOINTS should be 5");
        assertEquals(6, TabRegistry.TAB_HTTP, "TAB_HTTP should be 6");
        assertEquals(7, TabRegistry.TAB_HISTORY, "TAB_HISTORY should be 7");
        assertEquals(8, TabRegistry.TAB_ERRORS, "TAB_ERRORS should be 8");
        assertEquals(9, TabRegistry.TAB_MORE, "TAB_MORE should be 9");
    }

    @Test
    void numTabsMatchesLastTabPlusOne() {
        assertEquals(TabRegistry.TAB_MORE + 1, TabRegistry.NUM_TABS,
                "NUM_TABS should be one more than the last tab index");
    }

    @Test
    void numTabsIsTen() {
        assertEquals(10, TabRegistry.NUM_TABS, "NUM_TABS should be 10");
    }

    @Test
    void tabConstantsAreUnique() {
        int[] tabs = {
                TabRegistry.TAB_OVERVIEW, TabRegistry.TAB_LOG, TabRegistry.TAB_ACTIVITY,
                TabRegistry.TAB_DIAGRAM, TabRegistry.TAB_ROUTES, TabRegistry.TAB_ENDPOINTS,
                TabRegistry.TAB_HTTP, TabRegistry.TAB_HISTORY, TabRegistry.TAB_ERRORS,
                TabRegistry.TAB_MORE
        };
        for (int i = 0; i < tabs.length; i++) {
            for (int j = i + 1; j < tabs.length; j++) {
                assertTrue(tabs[i] != tabs[j],
                        "Tab constants should be unique, but index " + i + " and " + j + " are both " + tabs[i]);
            }
        }
    }

    @Test
    void allTabIndicesAreNonNegative() {
        assertTrue(TabRegistry.TAB_OVERVIEW >= 0, "TAB_OVERVIEW should be non-negative");
        assertTrue(TabRegistry.TAB_LOG >= 0, "TAB_LOG should be non-negative");
        assertTrue(TabRegistry.TAB_ACTIVITY >= 0, "TAB_ACTIVITY should be non-negative");
        assertTrue(TabRegistry.TAB_DIAGRAM >= 0, "TAB_DIAGRAM should be non-negative");
        assertTrue(TabRegistry.TAB_ROUTES >= 0, "TAB_ROUTES should be non-negative");
        assertTrue(TabRegistry.TAB_ENDPOINTS >= 0, "TAB_ENDPOINTS should be non-negative");
        assertTrue(TabRegistry.TAB_HTTP >= 0, "TAB_HTTP should be non-negative");
        assertTrue(TabRegistry.TAB_HISTORY >= 0, "TAB_HISTORY should be non-negative");
        assertTrue(TabRegistry.TAB_ERRORS >= 0, "TAB_ERRORS should be non-negative");
        assertTrue(TabRegistry.TAB_MORE >= 0, "TAB_MORE should be non-negative");
    }

    @Test
    void allTabIndicesAreLessThanNumTabs() {
        assertTrue(TabRegistry.TAB_OVERVIEW < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_LOG < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_ACTIVITY < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_DIAGRAM < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_ROUTES < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_ENDPOINTS < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_HTTP < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_HISTORY < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_ERRORS < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_MORE < TabRegistry.NUM_TABS);
    }
}
