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

import dev.tamboui.text.CharWidth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates {@link TuiIcons} primary tab emoji width (CAMEL-23818: avoid VS16 mismeasurement in TamboUI) and the
 * mnemonic and runtime/platform icon helpers. More-submenu icons and labels are validated in {@link TabRegistryTest}
 * where the {@link TabRegistry.MoreTab} records that own them are constructed.
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
        assertEquals(TuiIcons.TAB_LOG, TuiIcons.PRIMARY_TAB_ICONS.get(TabRegistry.TAB_LOG));
        assertEquals(TuiIcons.TAB_ACTIVITY, TuiIcons.PRIMARY_TAB_ICONS.get(TabRegistry.TAB_ACTIVITY));
        assertEquals(TuiIcons.TAB_DIAGRAM, TuiIcons.PRIMARY_TAB_ICONS.get(TabRegistry.TAB_DIAGRAM));
        assertEquals(TuiIcons.TAB_ROUTES, TuiIcons.PRIMARY_TAB_ICONS.get(TabRegistry.TAB_ROUTES));
        assertEquals(TuiIcons.TAB_ENDPOINTS, TuiIcons.PRIMARY_TAB_ICONS.get(TabRegistry.TAB_ENDPOINTS));
        assertEquals(TuiIcons.TAB_HTTP, TuiIcons.PRIMARY_TAB_ICONS.get(TabRegistry.TAB_HTTP));
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
    void primaryTabEmojisHaveNoVariationSelector() {
        for (String icon : TuiIcons.PRIMARY_TAB_ICONS) {
            assertFalse(icon.contains("\uFE0F"), "Icon should not contain VS16 variation selector: " + icon);
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
