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

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

class CanvasOverlay {

    private boolean visible;
    private Runnable onOpen;
    private Runnable onClose;

    void open() {
        if (onOpen != null) {
            onOpen.run();
        }
        visible = true;
    }

    void close() {
        visible = false;
        if (onClose != null) {
            onClose.run();
        }
    }

    boolean isVisible() {
        return visible;
    }

    void setOnOpen(Runnable onOpen) {
        this.onOpen = onOpen;
    }

    void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isCancel()) {
            close();
            return true;
        }
        return true;
    }

    void render(Frame frame, Rect area) {
        frame.renderWidget(Clear.INSTANCE, area);
    }

    void renderFooter(List<Span> spans) {
        hintLast(spans, "Esc", "close");
    }
}
