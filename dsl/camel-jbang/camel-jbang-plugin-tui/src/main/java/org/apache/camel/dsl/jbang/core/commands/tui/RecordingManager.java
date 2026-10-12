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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.export.ExportRequest;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

/**
 * Manages screenshot capture, tape recording, keystroke display, and screen buffer tracking. Extracted from
 * {@link CamelMonitor} to reduce class size.
 */
class RecordingManager {

    /** A key shown while recording; pressedAt is a {@link MonotonicClock} time. */
    record KeyRecord(String label, long pressedAt) {
    }

    private final CaptionOverlay captionOverlay;

    private boolean recording;
    private final AtomicReference<TapeRecorder> tapeRecorderRef = new AtomicReference<>();
    private volatile Buffer lastBuffer;
    private volatile long renderGeneration;
    private volatile String screenshotMessage;
    private volatile long screenshotMessageTime;
    private volatile boolean pendingScreenshot;
    private final List<KeyRecord> recentKeys = new ArrayList<>();
    private TuiEventLog eventLog;
    private int menuTapePosition = -1;

    RecordingManager(CaptionOverlay captionOverlay) {
        this.captionOverlay = captionOverlay;
    }

    void init(boolean initialRecording) {
        this.recording = initialRecording;
        this.eventLog = new TuiEventLog(500);
    }

    // ---- Key recording ----

    void recordKey(KeyEvent ke, boolean mcpInjected) {
        String label = keyLabel(ke);
        if (label != null) {
            if (eventLog != null) {
                eventLog.record(label, label);
            }
            if (recording) {
                recentKeys.add(new KeyRecord(label, MonotonicClock.millis()));
            }
            TapeRecorder tr = tapeRecorderRef.get();
            if (tr != null && tr.isActive() && !mcpInjected) {
                tr.recordKey(label);
            }
        }
    }

    // ---- Recording state ----

    boolean isRecording() {
        return recording;
    }

    void toggleRecording() {
        recording = !recording;
    }

    // ---- Screenshot ----

    void requestScreenshot() {
        pendingScreenshot = true;
    }

    boolean processPendingScreenshot() {
        if (pendingScreenshot) {
            pendingScreenshot = false;
            takeScreenshot();
            return true;
        }
        return false;
    }

    void takeScreenshot() {
        Buffer buf = lastBuffer;
        if (buf == null) {
            return;
        }
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String baseName = "camel-tui-screenshot-" + timestamp;
            Path svgPath = Path.of(baseName + ".svg");
            Path txtPath = Path.of(baseName + ".txt");
            Path ansPath = Path.of(baseName + ".ans");
            ExportRequest.export(buf).svg().toFile(svgPath);
            ExportRequest.export(buf).text().toFile(txtPath);
            ExportRequest.export(buf).text().options(o -> o.styles(true)).toFile(ansPath);
            screenshotMessage = "Screenshot saved to " + svgPath.toAbsolutePath() + " (and .txt, .ans)";
            screenshotMessageTime = MonotonicClock.millis();
        } catch (IOException e) {
            screenshotMessage = "Screenshot failed: " + e.getMessage();
            screenshotMessageTime = MonotonicClock.millis();
        }
    }

    // ---- Buffer tracking ----

    /**
     * Keeps a copy of the frame just rendered for the MCP tools, the tape player and screenshots. TamboUI clears and
     * draws into the same two buffers in turn, so another thread must not read the live one; the copy is one array of
     * references to immutable cells.
     */
    void updateBuffer(Buffer buffer) {
        lastBuffer = buffer.copy();
        renderGeneration++;
    }

    long getRenderGeneration() {
        return renderGeneration;
    }

    Buffer getLastBuffer() {
        return lastBuffer;
    }

    // ---- Screenshot flash message ----

    String screenshotFlashMessage() {
        String msg = screenshotMessage;
        if (msg != null && MonotonicClock.millis() - screenshotMessageTime < 5000) {
            return msg;
        }
        screenshotMessage = null;
        return null;
    }

    // ---- Tape menu rollback ----

    void markMenuOpened() {
        TapeRecorder tr = tapeRecorderRef.get();
        if (tr != null && tr.isActive()) {
            menuTapePosition = tr.preKeyPosition();
        }
    }

    void rollbackMenu() {
        TapeRecorder tr = tapeRecorderRef.get();
        if (tr != null && tr.isActive() && menuTapePosition >= 0) {
            tr.trimTo(menuTapePosition);
        }
        menuTapePosition = -1;
    }

    // ---- Tape recording ----

    AtomicReference<TapeRecorder> tapeRecorderRef() {
        return tapeRecorderRef;
    }

    TapeRecorder getTapeRecorder() {
        return tapeRecorderRef.get();
    }

    boolean isTapeRecording() {
        TapeRecorder rec = tapeRecorderRef.get();
        return rec != null && rec.isActive();
    }

    void startTapeRecording(String title) {
        TapeRecorder rec = new TapeRecorder();
        rec.start(title);
        tapeRecorderRef.set(rec);
    }

    void clearTapeRecorder() {
        tapeRecorderRef.set(null);
    }

    void toggleTapeRecording() {
        TapeRecorder rec = tapeRecorderRef.get();
        if (rec != null && rec.isActive()) {
            rollbackMenu();
            String tape = rec.stop();
            tapeRecorderRef.set(null);
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String filename = "camel-tui-tape-" + timestamp + ".tape";
            try {
                Files.writeString(Path.of(filename), tape);
                captionOverlay.showCaption("Tape saved: " + filename, 5);
            } catch (IOException e) {
                captionOverlay.showCaption("Failed to save tape: " + e.getMessage(), 5);
            }
        } else {
            rec = new TapeRecorder();
            rec.start(null);
            tapeRecorderRef.set(rec);
            captionOverlay.showCaption("Tape recording started", 3);
        }
    }

    // ---- Event log ----

    TuiEventLog getEventLog() {
        return eventLog;
    }

    // ---- Recent keys ----

    List<KeyRecord> getRecentKeys() {
        return recentKeys;
    }

    /** Drops the keys shown for two seconds; now is a {@link MonotonicClock} time. */
    void tickRecentKeys(long now) {
        if (recording && !recentKeys.isEmpty()) {
            long cutoff = now - 2000;
            recentKeys.removeIf(k -> k.pressedAt() < cutoff);
        }
    }

    // ---- Key label utility ----

    /**
     * The label of a key, as shown while recording and written to a tape: the key's name (Enter, F2, an arrow) or the
     * character typed, with Ctrl+, Alt+ and Shift+ in front when they were held (Shift+ with a character only together
     * with Ctrl or Alt, as the character shows Shift itself). Null for a key it has no name for.
     */
    static String keyLabel(KeyEvent ke) {
        String name = keyName(ke);
        if (name == null) {
            return null;
        }
        boolean character = ke.code() == KeyCode.CHAR && !"Space".equals(name);
        StringBuilder label = new StringBuilder();
        if (ke.hasCtrl()) {
            label.append("Ctrl+");
        }
        if (ke.hasAlt()) {
            label.append("Alt+");
        }
        if (ke.hasShift() && (!character || ke.hasCtrl() || ke.hasAlt())) {
            label.append("Shift+");
        }
        return label.append(name).toString();
    }

    private static String keyName(KeyEvent ke) {
        if (ke.isKey(KeyCode.ENTER)) {
            return "Enter";
        }
        if (ke.isKey(KeyCode.ESCAPE)) {
            return "Esc";
        }
        if (ke.isKey(KeyCode.TAB)) {
            return "Tab";
        }
        if (ke.isKey(KeyCode.UP)) {
            return TuiIcons.ARROW_UP;
        }
        if (ke.isKey(KeyCode.DOWN)) {
            return TuiIcons.ARROW_DOWN;
        }
        if (ke.isKey(KeyCode.LEFT)) {
            return TuiIcons.KEY_LEFT;
        }
        if (ke.isKey(KeyCode.RIGHT)) {
            return TuiIcons.KEY_RIGHT;
        }
        if (ke.isKey(KeyCode.PAGE_UP)) {
            return "PgUp";
        }
        if (ke.isKey(KeyCode.PAGE_DOWN)) {
            return "PgDn";
        }
        if (ke.isKey(KeyCode.HOME)) {
            return "Home";
        }
        if (ke.isKey(KeyCode.END)) {
            return "End";
        }
        if (ke.isKey(KeyCode.BACKSPACE)) {
            return "⌫";
        }
        if (ke.isKey(KeyCode.DELETE)) {
            return "Delete";
        }
        for (int i = 1; i <= 12; i++) {
            try {
                KeyCode fKey = KeyCode.valueOf("F" + i);
                if (ke.isKey(fKey)) {
                    return "F" + i;
                }
            } catch (IllegalArgumentException e) {
                break;
            }
        }
        if (ke.code() == KeyCode.CHAR) {
            String s = ke.string();
            if (" ".equals(s)) {
                return "Space";
            }
            if (!s.isEmpty()) {
                return s;
            }
        }
        return null;
    }
}
