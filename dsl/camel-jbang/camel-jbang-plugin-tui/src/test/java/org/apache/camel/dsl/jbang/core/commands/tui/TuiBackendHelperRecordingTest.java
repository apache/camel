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

import dev.tamboui.buffer.DiffResult;
import dev.tamboui.internal.record.AnsiTerminalCapture;
import dev.tamboui.layout.Position;
import dev.tamboui.layout.Size;
import dev.tamboui.terminal.Backend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.ClearSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code camel tui --record} actually engages TamboUI's recording backend.
 * <p>
 * TamboUI only wraps a backend for recording inside {@code BackendFactory.create()}, and {@code TuiRunner} calls that
 * factory <em>only</em> when no explicit backend is configured. Since camel-tui must supply an explicit
 * {@link dev.tamboui.backend.jline3.JLineBackend} (auto-discovery would otherwise pick the Aesh backend that is on the
 * classpath for {@code --web}), the recording wrapper is never applied unless camel-tui applies it itself. When that
 * wrapping is missing, {@code --record} replays no tape and writes no {@code .cast} file, yet still exits cleanly, so
 * only a test like this one catches the regression.
 */
// RecordingConfig.load() also reads fps and duration, so every key camel-tui sets has to be managed here: a value
// leaking out of this class would silently reconfigure recording in an unrelated test. junit-pioneer clears each key
// before the test and restores the original value afterwards, which also covers values the test body sets itself.
@ClearSystemProperty(key = "tamboui.record")
@ClearSystemProperty(key = "tamboui.record.config")
@ClearSystemProperty(key = "tamboui.record.width")
@ClearSystemProperty(key = "tamboui.record.height")
@ClearSystemProperty(key = "tamboui.record.duration")
@ClearSystemProperty(key = "tamboui.record.fps")
class TuiBackendHelperRecordingTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        // load() installs a System.out capture; restore the real stream so other tests are unaffected
        if (AnsiTerminalCapture.isInstalled()) {
            AnsiTerminalCapture.uninstall();
        }
    }

    @Test
    void withoutRecordOptionTheBackendIsHandedToTuiRunnerUntouched() {
        Backend original = new NoopBackend();

        Backend result = TuiBackendHelper.applyRecording(original);

        assertThat(result).isSameAs(original);
    }

    @Test
    void withRecordOptionTheBackendIsWrappedAndSizedFromTheRecordingConfig() throws Exception {
        Path tape = tempDir.resolve("demo.tape");
        Files.writeString(tape, "Sleep 100ms\nType \"q\"\n");
        System.setProperty("tamboui.record", tempDir.resolve("demo.cast").toString());
        System.setProperty("tamboui.record.config", tape.toString());
        System.setProperty("tamboui.record.width", "120");
        System.setProperty("tamboui.record.height", "30");

        Backend result = TuiBackendHelper.applyRecording(new NoopBackend());

        // A recording backend reports the configured cast dimensions rather than the real terminal
        // size; asserting on those proves the configuration was applied, not merely that some
        // wrapper was returned.
        assertThat(result).isNotInstanceOf(NoopBackend.class);
        assertThat(result.size()).isEqualTo(new Size(120, 30));
    }

    /**
     * Minimal {@link Backend} stand-in. Only {@link #size()} needs a meaningful value, so that the test fails if the
     * recording dimensions are taken from the delegate instead of the config.
     */
    private static final class NoopBackend implements Backend {

        @Override
        public void draw(DiffResult diff) throws IOException {
        }

        @Override
        public void flush() throws IOException {
        }

        @Override
        public void clear() throws IOException {
        }

        @Override
        public Size size() throws IOException {
            return new Size(80, 24);
        }

        @Override
        public void showCursor() throws IOException {
        }

        @Override
        public void hideCursor() throws IOException {
        }

        @Override
        public Position getCursorPosition() throws IOException {
            return new Position(0, 0);
        }

        @Override
        public void setCursorPosition(Position position) throws IOException {
        }

        @Override
        public void enterAlternateScreen() throws IOException {
        }

        @Override
        public void leaveAlternateScreen() throws IOException {
        }

        @Override
        public void enableRawMode() throws IOException {
        }

        @Override
        public void disableRawMode() throws IOException {
        }

        @Override
        public void onResize(Runnable handler) {
        }

        @Override
        public int read(int timeoutMs) throws IOException {
            return -2;
        }

        @Override
        public int peek(int timeoutMs) throws IOException {
            return -2;
        }

        @Override
        public void close() throws IOException {
        }
    }
}
