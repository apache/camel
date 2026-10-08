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

import dev.tamboui.buffer.DiffResult;
import dev.tamboui.layout.Position;
import dev.tamboui.layout.Size;
import dev.tamboui.terminal.Backend;

/**
 * Minimal {@link Backend} stand-in. Only {@link #size()} needs a meaningful value, so that the test fails if the
 * recording dimensions are taken from the delegate instead of the config.
 */
final class NoopBackend implements Backend {

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
    public void writeRaw(byte[] data) throws IOException {
        // TuiRunner.create enables bracketed paste through this method
    }

    @Override
    public void close() throws IOException {
    }
}
