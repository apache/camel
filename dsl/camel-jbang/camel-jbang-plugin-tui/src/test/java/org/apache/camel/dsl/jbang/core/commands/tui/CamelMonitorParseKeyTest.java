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
        KeyEvent ke = CamelMonitor.parseKey("a");
        assertNotNull(ke);
        assertEquals(KeyCode.CHAR, ke.code());
        assertEquals('a', ke.character());
    }

    @Test
    void parseKeyEnter() {
        KeyEvent ke = CamelMonitor.parseKey("enter");
        assertNotNull(ke);
        assertEquals(KeyCode.ENTER, ke.code());
    }

    @Test
    void parseKeyReturn() {
        KeyEvent ke = CamelMonitor.parseKey("return");
        assertNotNull(ke);
        assertEquals(KeyCode.ENTER, ke.code());
    }

    @Test
    void parseKeyEscape() {
        KeyEvent ke = CamelMonitor.parseKey("esc");
        assertNotNull(ke);
        assertEquals(KeyCode.ESCAPE, ke.code());
    }

    @Test
    void parseKeyEscapeFull() {
        KeyEvent ke = CamelMonitor.parseKey("escape");
        assertNotNull(ke);
        assertEquals(KeyCode.ESCAPE, ke.code());
    }

    @Test
    void parseKeyTab() {
        KeyEvent ke = CamelMonitor.parseKey("tab");
        assertNotNull(ke);
        assertEquals(KeyCode.TAB, ke.code());
    }

    @Test
    void parseKeyBackspace() {
        KeyEvent ke = CamelMonitor.parseKey("backspace");
        assertNotNull(ke);
        assertEquals(KeyCode.BACKSPACE, ke.code());
    }

    @Test
    void parseKeyDelete() {
        KeyEvent ke = CamelMonitor.parseKey("delete");
        assertNotNull(ke);
        assertEquals(KeyCode.DELETE, ke.code());
    }

    @Test
    void parseKeyDeleteShort() {
        KeyEvent ke = CamelMonitor.parseKey("del");
        assertNotNull(ke);
        assertEquals(KeyCode.DELETE, ke.code());
    }

    @Test
    void parseKeyArrows() {
        assertEquals(KeyCode.UP, CamelMonitor.parseKey("up").code());
        assertEquals(KeyCode.DOWN, CamelMonitor.parseKey("down").code());
        assertEquals(KeyCode.LEFT, CamelMonitor.parseKey("left").code());
        assertEquals(KeyCode.RIGHT, CamelMonitor.parseKey("right").code());
    }

    @Test
    void parseKeyHomeEnd() {
        assertEquals(KeyCode.HOME, CamelMonitor.parseKey("home").code());
        assertEquals(KeyCode.END, CamelMonitor.parseKey("end").code());
    }

    @Test
    void parseKeyPageUpDown() {
        assertEquals(KeyCode.PAGE_UP, CamelMonitor.parseKey("pageup").code());
        assertEquals(KeyCode.PAGE_UP, CamelMonitor.parseKey("pgup").code());
        assertEquals(KeyCode.PAGE_DOWN, CamelMonitor.parseKey("pagedown").code());
        assertEquals(KeyCode.PAGE_DOWN, CamelMonitor.parseKey("pgdn").code());
    }

    @Test
    void parseKeyFKeys() {
        assertEquals(KeyCode.F1, CamelMonitor.parseKey("f1").code());
        assertEquals(KeyCode.F6, CamelMonitor.parseKey("f6").code());
        assertEquals(KeyCode.F12, CamelMonitor.parseKey("f12").code());
    }

    @Test
    void parseKeySpace() {
        KeyEvent ke = CamelMonitor.parseKey("space");
        assertNotNull(ke);
        assertEquals(KeyCode.CHAR, ke.code());
        assertEquals(' ', ke.character());
    }

    @Test
    void parseKeyCtrlModifier() {
        KeyEvent ke = CamelMonitor.parseKey("Ctrl+c");
        assertNotNull(ke);
        assertEquals(KeyCode.CHAR, ke.code());
        assertEquals('c', ke.character());
        assertTrue(ke.hasCtrl());
    }

    @Test
    void parseKeyShiftModifier() {
        KeyEvent ke = CamelMonitor.parseKey("Shift+F6");
        assertNotNull(ke);
        assertEquals(KeyCode.F6, ke.code());
        assertTrue(ke.hasShift());
    }

    @Test
    void parseKeyCtrlAndShift() {
        KeyEvent ke = CamelMonitor.parseKey("Ctrl+Shift+a");
        assertNotNull(ke);
        assertTrue(ke.hasCtrl());
        assertTrue(ke.hasShift());
    }

    @Test
    void parseKeyNullReturnsNull() {
        assertNull(CamelMonitor.parseKey(null));
    }

    @Test
    void parseKeyEmptyReturnsNull() {
        assertNull(CamelMonitor.parseKey(""));
    }

    @Test
    void parseKeyCaseInsensitive() {
        KeyEvent ke1 = CamelMonitor.parseKey("ENTER");
        assertNotNull(ke1);
        assertEquals(KeyCode.ENTER, ke1.code());

        KeyEvent ke2 = CamelMonitor.parseKey("Enter");
        assertNotNull(ke2);
        assertEquals(KeyCode.ENTER, ke2.code());
    }

    @Test
    void parseKeyUnknownMultiCharReturnsNull() {
        // Multi-character string that is not a known key name
        assertNull(CamelMonitor.parseKey("xyz"));
    }
}
