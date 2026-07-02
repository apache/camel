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

import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;

/**
 * Reusable drag-to-resize helper for split panel borders. Tracks press/drag/release on a border position (vertical or
 * horizontal) and reports whether the mouse event was consumed.
 */
class DragSplit {

    private int borderPos = -1;
    private boolean dragging;

    void setBorderPos(int pos) {
        this.borderPos = pos;
    }

    void clearBorderPos() {
        this.borderPos = -1;
    }

    boolean isDragging() {
        return dragging;
    }

    /**
     * Handle a mouse event for this split border.
     *
     * @param  me       the mouse event
     * @param  mousePos the mouse coordinate on the split axis (me.y() for vertical, me.x() for horizontal)
     * @return          true if the event was consumed (press near border, drag while active, or release)
     */
    boolean handleMouse(MouseEvent me, int mousePos) {
        if (borderPos < 0) {
            return false;
        }
        if (me.isPress() && Math.abs(mousePos - borderPos) <= 1) {
            dragging = true;
            return true;
        }
        if (dragging && me.kind() == MouseEventKind.DRAG) {
            return true;
        }
        if (dragging && me.isRelease()) {
            dragging = false;
            return true;
        }
        return false;
    }
}
