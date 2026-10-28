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

import java.util.ArrayList;
import java.util.List;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TocPopupTest {

    @Test
    void aClickInAScrolledListPicksTheHeadingUnderTheMouse() {
        TocPopup popup = new TocPopup();
        popup.open(headings(40));
        // the last heading is selected, so the list is scrolled down
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.END));
        Rect area = new Rect(0, 0, 80, 20);
        Buffer buffer = Buffer.empty(area);
        popup.render(Frame.forTesting(buffer), area);

        int[] cell = find(buffer, "Heading 35");
        popup.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, cell[0], cell[1]));

        assertThat(popup.consumePendingEntry().title()).isEqualTo("Heading 35");
    }

    @Test
    void theScrollbarIsOnTheBorderAtTheScrollPosition() {
        TocPopup popup = new TocPopup();
        popup.open(headings(40));
        Rect area = new Rect(0, 0, 80, 20);

        Buffer top = Buffer.empty(area);
        popup.render(Frame.forTesting(top), area);
        int[] corner = find(top, "╮");
        int bottom = find(top, "╯")[1];
        // the corners stay, and the thumb starts below the top corner while the list is at its top
        assertThat(top.get(corner[0], corner[1]).symbol()).isEqualTo("╮");
        assertThat(top.get(corner[0], bottom).symbol()).isEqualTo("╯");
        assertThat(top.get(corner[0], corner[1] + 1).symbol()).isEqualTo("█");

        // at the end of the list the thumb is at the bottom of the track
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.END));
        Buffer end = Buffer.empty(area);
        popup.render(Frame.forTesting(end), area);
        assertThat(end.get(corner[0], bottom).symbol()).isEqualTo("╯");
        assertThat(end.get(corner[0], bottom - 1).symbol()).isEqualTo("█");
        assertThat(end.get(corner[0], corner[1] + 1).symbol()).isNotEqualTo("█");
    }

    private static List<TocPopup.TocEntry> headings(int count) {
        List<TocPopup.TocEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(new TocPopup.TocEntry(2, "Heading " + i, i * 100));
        }
        return entries;
    }

    private static int[] find(Buffer buffer, String text) {
        String[] rows = TuiTestHelper.bufferToString(buffer).split("\n");
        for (int y = 0; y < rows.length; y++) {
            int x = rows[y].indexOf(text);
            if (x >= 0) {
                return new int[] { x, y };
            }
        }
        throw new AssertionError(text + " is not on the screen");
    }
}
