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
import java.util.Map;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The blank row between the groups and the hint of the example browser is a list item of its own: it takes a row, and a
 * click is mapped to the item drawn at that row.
 */
class ExampleBrowserPopupTest {

    private static final Rect AREA = new Rect(0, 0, 140, 40);

    @Test
    void theBlankSpacerTakesARowAndAClickSelectsTheGroupDrawnThere() {
        Map<String, List<JsonObject>> groups = ExampleHelper.groupByLevel(ExampleHelper.loadCatalog());
        assertThat(groups).isNotEmpty();
        String lastGroup = ExampleHelper.getGroupTitle(new ArrayList<>(groups.keySet()).get(groups.size() - 1));

        ExampleBrowserPopup popup = new ExampleBrowserPopup(null, null);
        popup.open();
        List<String> rows = render(popup);

        // the groups start below the top border, then the blank spacer, then the hint
        int lastGroupRow = rowContaining(rows, lastGroup + " (");
        int hintRow = rowContaining(rows, "Enter opens a group");
        assertThat(hintRow).isEqualTo(lastGroupRow + 2);
        assertThat(rows.get(lastGroupRow + 1).replaceAll("[│ ]", "")).isEmpty();

        // a click on the spacer or the hint selects nothing, a click on the last group selects it
        popup.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, 20, hintRow - 1));
        popup.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, 20, hintRow));
        popup.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, 20, lastGroupRow));
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertThat(render(popup).get(2)).contains(" " + lastGroup + " (");
    }

    private static List<String> render(ExampleBrowserPopup popup) {
        Buffer buffer = Buffer.empty(AREA);
        popup.render(Frame.forTesting(buffer), AREA);
        List<String> rows = new ArrayList<>();
        for (int y = 0; y < AREA.height(); y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < AREA.width(); x++) {
                if (!buffer.get(x, y).isContinuation()) {
                    row.append(buffer.get(x, y).symbol());
                }
            }
            rows.add(row.toString());
        }
        return rows;
    }

    private static int rowContaining(List<String> rows, String text) {
        for (int y = 0; y < rows.size(); y++) {
            if (rows.get(y).contains(text)) {
                return y;
            }
        }
        throw new AssertionError("no row contains " + text + ":\n" + String.join("\n", rows));
    }
}
