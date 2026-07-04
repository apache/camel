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

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelMonitorParseKeyTest {

    @Test
    void parseKeySingleChar() {
        KeyEvent ke = McpFacade.parseKey("a");
        assertNotNull(ke);
        assertEquals(KeyCode.CHAR, ke.code());
        assertEquals('a', ke.character());
    }

    @Test
    void parseKeyEnter() {
        KeyEvent ke = McpFacade.parseKey("enter");
        assertNotNull(ke);
        assertEquals(KeyCode.ENTER, ke.code());
    }

    @Test
    void parseKeyReturn() {
        KeyEvent ke = McpFacade.parseKey("return");
        assertNotNull(ke);
        assertEquals(KeyCode.ENTER, ke.code());
    }

    @Test
    void parseKeyEscape() {
        KeyEvent ke = McpFacade.parseKey("esc");
        assertNotNull(ke);
        assertEquals(KeyCode.ESCAPE, ke.code());
    }

    @Test
    void parseKeyEscapeFull() {
        KeyEvent ke = McpFacade.parseKey("escape");
        assertNotNull(ke);
        assertEquals(KeyCode.ESCAPE, ke.code());
    }

    @Test
    void parseKeyTab() {
        KeyEvent ke = McpFacade.parseKey("tab");
        assertNotNull(ke);
        assertEquals(KeyCode.TAB, ke.code());
    }

    @Test
    void parseKeyBackspace() {
        KeyEvent ke = McpFacade.parseKey("backspace");
        assertNotNull(ke);
        assertEquals(KeyCode.BACKSPACE, ke.code());
    }

    @Test
    void parseKeyDelete() {
        KeyEvent ke = McpFacade.parseKey("delete");
        assertNotNull(ke);
        assertEquals(KeyCode.DELETE, ke.code());
    }

    @Test
    void parseKeyDeleteShort() {
        KeyEvent ke = McpFacade.parseKey("del");
        assertNotNull(ke);
        assertEquals(KeyCode.DELETE, ke.code());
    }

    @Test
    void parseKeyArrows() {
        assertEquals(KeyCode.UP, McpFacade.parseKey("up").code());
        assertEquals(KeyCode.DOWN, McpFacade.parseKey("down").code());
        assertEquals(KeyCode.LEFT, McpFacade.parseKey("left").code());
        assertEquals(KeyCode.RIGHT, McpFacade.parseKey("right").code());
    }

    @Test
    void parseKeyHomeEnd() {
        assertEquals(KeyCode.HOME, McpFacade.parseKey("home").code());
        assertEquals(KeyCode.END, McpFacade.parseKey("end").code());
    }

    @Test
    void parseKeyPageUpDown() {
        assertEquals(KeyCode.PAGE_UP, McpFacade.parseKey("pageup").code());
        assertEquals(KeyCode.PAGE_UP, McpFacade.parseKey("pgup").code());
        assertEquals(KeyCode.PAGE_DOWN, McpFacade.parseKey("pagedown").code());
        assertEquals(KeyCode.PAGE_DOWN, McpFacade.parseKey("pgdn").code());
    }

    @Test
    void parseKeyFKeys() {
        assertEquals(KeyCode.F1, McpFacade.parseKey("f1").code());
        assertEquals(KeyCode.F6, McpFacade.parseKey("f6").code());
        assertEquals(KeyCode.F12, McpFacade.parseKey("f12").code());
    }

    @Test
    void parseKeySpace() {
        KeyEvent ke = McpFacade.parseKey("space");
        assertNotNull(ke);
        assertEquals(KeyCode.CHAR, ke.code());
        assertEquals(' ', ke.character());
    }

    @Test
    void parseKeyCtrlModifier() {
        KeyEvent ke = McpFacade.parseKey("Ctrl+c");
        assertNotNull(ke);
        assertEquals(KeyCode.CHAR, ke.code());
        assertEquals('c', ke.character());
        assertTrue(ke.hasCtrl());
    }

    @Test
    void parseKeyShiftModifier() {
        KeyEvent ke = McpFacade.parseKey("Shift+F6");
        assertNotNull(ke);
        assertEquals(KeyCode.F6, ke.code());
        assertTrue(ke.hasShift());
    }

    @Test
    void parseKeyCtrlAndShift() {
        KeyEvent ke = McpFacade.parseKey("Ctrl+Shift+a");
        assertNotNull(ke);
        assertTrue(ke.hasCtrl());
        assertTrue(ke.hasShift());
    }

    @Test
    void parseKeyNullReturnsNull() {
        assertNull(McpFacade.parseKey(null));
    }

    @Test
    void parseKeyEmptyReturnsNull() {
        assertNull(McpFacade.parseKey(""));
    }

    @Test
    void parseKeyCaseInsensitive() {
        KeyEvent ke1 = McpFacade.parseKey("ENTER");
        assertNotNull(ke1);
        assertEquals(KeyCode.ENTER, ke1.code());

        KeyEvent ke2 = McpFacade.parseKey("Enter");
        assertNotNull(ke2);
        assertEquals(KeyCode.ENTER, ke2.code());
    }

    @Test
    void parseKeyUnknownMultiCharReturnsNull() {
        // Multi-character string that is not a known key name
        assertNull(McpFacade.parseKey("xyz"));
    }
}
