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

import java.util.List;

import dev.tamboui.text.CharWidth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates {@link TuiIcons} emoji widths (CAMEL-23818: every icon has to take the 2 columns TamboUI reserves for it,
 * using glyphs that are emoji by default, as text-default glyphs with VS16 are drawn 1 column wide on many terminals)
 * and the mnemonic and runtime/platform icon helpers. More-submenu icons and labels are validated in
 * {@link TabRegistryTest} where the {@link TabRegistry.MoreTab} records that own them are constructed.
 */
class TuiIconsTest {

    @Test
    void primaryTabIconCountMatchesTabRegistry() {
        assertEquals(TabRegistry.NUM_TABS, TuiIcons.PRIMARY_TAB_ICONS.size());
    }

    @Test
    void primaryTabIconsAreOrderedByTabIndex() {
        // Guards that PRIMARY_TAB_ICONS is indexed by the TAB_* constants: reordering the list must break this,
        // otherwise TabRegistry.icon(index) would attach the wrong emoji to Go-to entries.
        assertEquals(TuiIcons.TAB_OVERVIEW, TuiIcons.PRIMARY_TAB_ICONS.get(TabRegistry.TAB_OVERVIEW));
        assertEquals(TuiIcons.TAB_SOURCE, TuiIcons.PRIMARY_TAB_ICONS.get(TabRegistry.TAB_SOURCE));
        assertEquals(TuiIcons.TAB_LOG, TuiIcons.PRIMARY_TAB_ICONS.get(TabRegistry.TAB_LOG));
        assertEquals(TuiIcons.TAB_ACTIVITY, TuiIcons.PRIMARY_TAB_ICONS.get(TabRegistry.TAB_ACTIVITY));
        assertEquals(TuiIcons.TAB_DIAGRAM, TuiIcons.PRIMARY_TAB_ICONS.get(TabRegistry.TAB_DIAGRAM));
        assertEquals(TuiIcons.TAB_ROUTES, TuiIcons.PRIMARY_TAB_ICONS.get(TabRegistry.TAB_ROUTES));
        assertEquals(TuiIcons.TAB_ENDPOINTS, TuiIcons.PRIMARY_TAB_ICONS.get(TabRegistry.TAB_ENDPOINTS));
        assertEquals(TuiIcons.TAB_INSPECT, TuiIcons.PRIMARY_TAB_ICONS.get(TabRegistry.TAB_HISTORY));
        assertEquals(TuiIcons.TAB_ERRORS, TuiIcons.PRIMARY_TAB_ICONS.get(TabRegistry.TAB_ERRORS));
        assertEquals(TuiIcons.TAB_MORE, TuiIcons.PRIMARY_TAB_ICONS.get(TabRegistry.TAB_MORE));
    }

    @Test
    void primaryTabEmojisAreTwoColumnsWide() {
        for (String icon : TuiIcons.PRIMARY_TAB_ICONS) {
            assertEquals(2, CharWidth.of(icon), "Icon should be 2 terminal columns wide: " + icon);
        }
    }

    @Test
    void menuEmojisAreEmojiByDefaultAndTwoColumnsWide() {
        // text-default glyphs (keyboard, record, stop, wastebasket) with VS16 are drawn in 1 column on many terminals,
        // so the menu uses glyphs that are emoji by default
        for (String icon : List.of(TuiIcons.KEYSTROKES, TuiIcons.RECORD, TuiIcons.STOP_RECORD, TuiIcons.DELETE)) {
            assertFalse(icon.contains("\uFE0F"), "Icon should not need the VS16 variation selector: " + icon);
            assertEquals(2, CharWidth.of(icon), "Icon should be 2 terminal columns wide: " + icon);
        }
    }

    @Test
    void stripMnemonicRemovesMarkerAndIndexPointsAtShortcutLetter() {
        assertEquals("Browse", TuiIcons.stripMnemonic("Bro&wse"));
        assertEquals(3, TuiIcons.mnemonicIndex("Bro&wse"));
        assertEquals('w', TuiIcons.stripMnemonic("Bro&wse").charAt(TuiIcons.mnemonicIndex("Bro&wse")));
        assertEquals("Plain", TuiIcons.stripMnemonic("Plain"));
        assertEquals(-1, TuiIcons.mnemonicIndex("Plain"));
    }

    @Test
    void runtimePlatformAndProfileIconsCoverKnownAndDefaultBranches() {
        assertEquals(TuiIcons.SPRING_BOOT, TuiIcons.runtimeIcon("Spring Boot"));
        assertEquals(TuiIcons.QUARKUS, TuiIcons.runtimeIcon("Quarkus"));
        assertEquals(TuiIcons.CAMEL, TuiIcons.runtimeIcon("Something else"));

        assertEquals(TuiIcons.QUARKUS + " ", TuiIcons.platformIcon("Quarkus"));
        assertEquals(TuiIcons.CAMEL + " ", TuiIcons.platformIcon("JBang"));
        assertTrue(TuiIcons.platformIcon("unknown").isEmpty());

        assertEquals(TuiIcons.DEV_PROFILE + " ", TuiIcons.profilePrefix("dev"));
        assertEquals(TuiIcons.PROD_PROFILE + " ", TuiIcons.profilePrefix("prod"));
        assertTrue(TuiIcons.profilePrefix("staging").isEmpty());
    }
}
