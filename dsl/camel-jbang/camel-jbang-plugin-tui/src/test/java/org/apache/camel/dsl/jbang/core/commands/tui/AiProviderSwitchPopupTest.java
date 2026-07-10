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

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProviderSwitchPopupTest {

    private static KeyEvent key(KeyCode code) {
        return KeyEvent.ofKey(code, KeyModifiers.NONE);
    }

    @Test
    void enterSelectsHighlightedChoice() {
        AiProviderSwitchPopup popup = new AiProviderSwitchPopup();
        popup.open(List.of(
                new AiProviderSwitchPopup.ProviderChoice("auto", "", "", true),
                new AiProviderSwitchPopup.ProviderChoice("gemini", "gemini-3.5-flash", "", false)));

        popup.handleKeyEvent(key(KeyCode.DOWN));
        popup.handleKeyEvent(key(KeyCode.ENTER));

        AiProviderSwitchPopup.ProviderChoice selected = popup.consumePendingChoice();
        assertEquals("gemini", selected.provider());
        assertEquals("gemini-3.5-flash", selected.model());
        assertFalse(popup.isVisible());
    }

    @Test
    void escapeCancelsWithoutSelection() {
        AiProviderSwitchPopup popup = new AiProviderSwitchPopup();
        popup.open(List.of(new AiProviderSwitchPopup.ProviderChoice("auto", "", "", true)));

        assertTrue(popup.isVisible());
        popup.handleKeyEvent(key(KeyCode.ESCAPE));

        assertFalse(popup.isVisible());
        assertNull(popup.consumePendingChoice());
    }
}
