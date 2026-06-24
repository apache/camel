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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShellPanelTest {

    // contentLength fed to the scrollbar is historySize + viewportHeight, so the live view sits at the
    // bottom of the track (position == historySize) and the maximum scrollback sits at the top.

    @Test
    void liveViewSitsAtBottomOfScrollback() {
        // scrollOffset 0 means "following live output": the viewport's first line is the last history line,
        // i.e. the thumb is pinned to the bottom of the track.
        assertEquals(100, ShellPanel.scrollbarPosition(100, 20, 0));
    }

    @Test
    void maxScrollbackSitsAtTop() {
        // scrolling back by the full history size brings the very first history line into view.
        assertEquals(0, ShellPanel.scrollbarPosition(100, 20, 100));
    }

    @Test
    void partialScrollMovesViewportProportionally() {
        // 100 history + 20 viewport = 120 total lines; scrolled back 30 -> first visible line is 120-30-20.
        assertEquals(70, ShellPanel.scrollbarPosition(100, 20, 30));
    }

    @Test
    void overScrollIsClampedToTop() {
        // a scrollOffset larger than the history must not produce a negative position.
        assertEquals(0, ShellPanel.scrollbarPosition(100, 20, 500));
    }

    @Test
    void noHistoryKeepsViewportAtTop() {
        // with no scrollback history the viewport is always pinned at the top of the live screen.
        assertEquals(0, ShellPanel.scrollbarPosition(0, 20, 0));
    }
}
