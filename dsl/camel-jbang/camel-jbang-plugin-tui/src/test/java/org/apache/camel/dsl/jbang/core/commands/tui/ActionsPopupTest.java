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

import dev.tamboui.layout.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link ActionsPopup#listItemAt}, which maps a click to an entry in a single-line, bordered list popup. The
 * popup has a one-cell border on every side, so the first entry sits at {@code popup.y() + 1} and the interior spans
 * {@code [popup.x() + 1, popup.x() + width - 1)}. For scrolled lists, {@code offset} is the index of the first visible
 * entry, so on-screen row r maps to entry {@code offset + (r - firstRow)}.
 */
class ActionsPopupTest {

    @Test
    void resolvesClicksToEntriesWhenNotScrolled() {
        Rect popup = new Rect(0, 0, 40, 12);
        assertEquals(0, ActionsPopup.listItemAt(popup, 0, 20, 5, 1), "first row under the top border is entry 0");
        assertEquals(3, ActionsPopup.listItemAt(popup, 0, 20, 5, 4), "the fourth interior row is entry 3");
    }

    @Test
    void appliesScrollOffsetToClickedRow() {
        // The list is scrolled so the first visible entry is index 7; the top interior row therefore maps to entry 7.
        Rect popup = new Rect(0, 0, 40, 12);
        assertEquals(7, ActionsPopup.listItemAt(popup, 7, 50, 5, 1), "offset shifts the first visible row to entry 7");
        assertEquals(9, ActionsPopup.listItemAt(popup, 7, 50, 5, 3), "third visible row maps to entry 7 + 2");
    }

    @Test
    void rejectsBorderAndOutsideClicks() {
        Rect popup = new Rect(0, 0, 40, 12);
        assertEquals(-1, ActionsPopup.listItemAt(popup, 0, 20, 5, 0), "the top border row is not an entry");
        assertEquals(-1, ActionsPopup.listItemAt(popup, 0, 20, 0, 5), "the left border column is not an entry");
        assertEquals(-1, ActionsPopup.listItemAt(popup, 0, 20, 39, 5), "the right border column is not an entry");
        assertEquals(-1, ActionsPopup.listItemAt(popup, 0, 20, 5, 11), "the bottom border row is not an entry");
        assertEquals(-1, ActionsPopup.listItemAt(popup, 0, 20, 60, 5), "a click outside the popup is not an entry");
    }

    @Test
    void rejectsRowsPastTheLastEntry() {
        // A popup with more interior rows than entries: a click below the last entry must not resolve to a phantom row.
        Rect popup = new Rect(0, 0, 40, 12);
        assertEquals(-1, ActionsPopup.listItemAt(popup, 0, 3, 5, 5), "row 4 has no entry when only 3 entries exist");
        // With an offset, an interior row that lands past the end is likewise rejected.
        assertEquals(-1, ActionsPopup.listItemAt(popup, 48, 50, 5, 4), "offset 48 + row 3 = entry 51 exceeds 50 entries");
    }

    @Test
    void handlesNullGeometry() {
        assertEquals(-1, ActionsPopup.listItemAt(null, 0, 20, 5, 5), "no captured geometry yet");
    }
}
