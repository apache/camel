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
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RecordingManager} state management and key label utility.
 */
class RecordingManagerTest {

    private RecordingManager manager;

    @BeforeEach
    void setUp() {
        manager = new RecordingManager(new CaptionOverlay());
        manager.init(false);
    }

    @Test
    void initialStateIsNotRecording() {
        assertFalse(manager.isRecording(), "Should not be recording initially");
    }

    @Test
    void toggleRecordingChangesState() {
        assertFalse(manager.isRecording());
        manager.toggleRecording();
        assertTrue(manager.isRecording(), "Should be recording after toggle");
        manager.toggleRecording();
        assertFalse(manager.isRecording(), "Should not be recording after second toggle");
    }

    @Test
    void initWithRecordingEnabled() {
        RecordingManager rm = new RecordingManager(new CaptionOverlay());
        rm.init(true);
        assertTrue(rm.isRecording(), "Should be recording when initialized with true");
    }

    @Test
    void requestScreenshotSetsFlag() {
        manager.requestScreenshot();
        // processPendingScreenshot returns true when a screenshot was pending
        // but won't actually save since lastBuffer is null
        assertTrue(manager.processPendingScreenshot(),
                "Should have pending screenshot after request");
    }

    @Test
    void processPendingScreenshotReturnsFalseWhenNoPending() {
        assertFalse(manager.processPendingScreenshot(),
                "Should return false when no screenshot is pending");
    }

    @Test
    void renderGenerationStartsAtZero() {
        assertEquals(0, manager.getRenderGeneration(),
                "Render generation should start at 0");
    }

    @Test
    void lastBufferIsNullInitially() {
        assertNull(manager.getLastBuffer(), "Last buffer should be null initially");
    }

    @Test
    void eventLogIsInitialized() {
        assertNotNull(manager.getEventLog(), "Event log should be initialized after init()");
    }

    @Test
    void recentKeysIsEmptyInitially() {
        assertTrue(manager.getRecentKeys().isEmpty(), "Recent keys should be empty initially");
    }

    @Test
    void tapeRecorderIsNullInitially() {
        assertNull(manager.getTapeRecorder(), "Tape recorder should be null initially");
        assertFalse(manager.isTapeRecording(), "Should not be tape recording initially");
    }

    // ---- keyLabel tests ----

    @Test
    void keyLabelForEnter() {
        KeyEvent ke = KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE);
        assertEquals("Enter", RecordingManager.keyLabel(ke));
    }

    @Test
    void keyLabelForEscape() {
        KeyEvent ke = KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE);
        assertEquals("Esc", RecordingManager.keyLabel(ke));
    }

    @Test
    void keyLabelForTab() {
        KeyEvent ke = KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.NONE);
        assertEquals("Tab", RecordingManager.keyLabel(ke));
    }

    @Test
    void keyLabelForArrowKeys() {
        assertEquals("↑", RecordingManager.keyLabel(KeyEvent.ofKey(KeyCode.UP, KeyModifiers.NONE)));
        assertEquals("↓", RecordingManager.keyLabel(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE)));
        assertEquals("←", RecordingManager.keyLabel(KeyEvent.ofKey(KeyCode.LEFT, KeyModifiers.NONE)));
        assertEquals("→", RecordingManager.keyLabel(KeyEvent.ofKey(KeyCode.RIGHT, KeyModifiers.NONE)));
    }

    @Test
    void keyLabelForPageKeys() {
        assertEquals("PgUp", RecordingManager.keyLabel(KeyEvent.ofKey(KeyCode.PAGE_UP, KeyModifiers.NONE)));
        assertEquals("PgDn", RecordingManager.keyLabel(KeyEvent.ofKey(KeyCode.PAGE_DOWN, KeyModifiers.NONE)));
    }

    @Test
    void keyLabelForHomeEnd() {
        assertEquals("Home", RecordingManager.keyLabel(KeyEvent.ofKey(KeyCode.HOME, KeyModifiers.NONE)));
        assertEquals("End", RecordingManager.keyLabel(KeyEvent.ofKey(KeyCode.END, KeyModifiers.NONE)));
    }

    @Test
    void keyLabelForBackspace() {
        assertEquals("⌫", RecordingManager.keyLabel(KeyEvent.ofKey(KeyCode.BACKSPACE, KeyModifiers.NONE)));
    }

    @Test
    void keyLabelForCharKey() {
        KeyEvent ke = KeyEvent.ofChar('s', KeyModifiers.NONE);
        assertEquals("s", RecordingManager.keyLabel(ke));
    }

    @Test
    void keyLabelForSpace() {
        KeyEvent ke = KeyEvent.ofChar(' ', KeyModifiers.NONE);
        assertEquals("Space", RecordingManager.keyLabel(ke));
    }

    @Test
    void keyLabelForFunctionKey() {
        KeyEvent ke = KeyEvent.ofKey(KeyCode.F5, KeyModifiers.NONE);
        assertEquals("F5", RecordingManager.keyLabel(ke));
    }

    @Test
    void recordKeyAddsToRecentKeysWhenRecording() {
        manager.toggleRecording(); // enable recording
        assertTrue(manager.isRecording());

        manager.recordKey(KeyEvent.ofChar('a', KeyModifiers.NONE), false);
        assertFalse(manager.getRecentKeys().isEmpty(),
                "Recent keys should have entries after recording a key");
    }

    @Test
    void recordKeyDoesNotAddWhenNotRecording() {
        assertFalse(manager.isRecording());

        manager.recordKey(KeyEvent.ofChar('a', KeyModifiers.NONE), false);
        assertTrue(manager.getRecentKeys().isEmpty(),
                "Recent keys should be empty when not recording");
    }

    @Test
    void tickRecentKeysRemovesOldEntries() {
        manager.toggleRecording();
        manager.recordKey(KeyEvent.ofChar('a', KeyModifiers.NONE), false);
        assertFalse(manager.getRecentKeys().isEmpty());

        // Tick with a time far in the future to expire all entries
        manager.tickRecentKeys(System.currentTimeMillis() + 10_000);
        assertTrue(manager.getRecentKeys().isEmpty(),
                "Old key records should be removed after tick");
    }
}
