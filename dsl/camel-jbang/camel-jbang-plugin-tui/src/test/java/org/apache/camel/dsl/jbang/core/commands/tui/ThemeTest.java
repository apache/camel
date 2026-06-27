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

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ThemeTest {

    @Test
    void accentIsBrandOrange() {
        // Guards against accidental palette drift: the brand accent must stay Camel orange #F69123.
        assertEquals(Color.rgb(0xF6, 0x91, 0x23), Theme.accent());
        assertEquals(Color.rgb(0xF6, 0x91, 0x23), Theme.ACCENT);
    }

    @Test
    void exposesAllSemanticStyles() {
        // Each semantic surface must exist so call sites reference intent, not raw colors.
        assertNotNull(Theme.accentBg());
        assertNotNull(Theme.border());
        assertNotNull(Theme.borderFocused());
        assertNotNull(Theme.title());
        assertNotNull(Theme.success());
        assertNotNull(Theme.warning());
        assertNotNull(Theme.error());
        assertNotNull(Theme.muted());
        assertNotNull(Theme.selectionBg());
    }

    @Test
    void focusedBorderUsesAccentUnfocusedDoesNot() {
        // The focus cue is the whole point: focused border is orange, unfocused is not.
        assertEquals(Style.EMPTY.fg(Theme.ACCENT), Theme.borderFocused());
        assertEquals(Style.EMPTY.fg(Color.DARK_GRAY), Theme.border());
    }
}
