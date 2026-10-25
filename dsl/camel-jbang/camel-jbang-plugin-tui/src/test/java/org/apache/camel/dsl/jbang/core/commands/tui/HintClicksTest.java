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

import dev.tamboui.layout.Rect;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HintClicksTest {

    @BeforeEach
    void resetTheme() {
        Theme.resetForTesting();
        Theme.setMode("dark");
    }

    @Test
    void keyEventParsesFunctionKeys() {
        assertThat(HintClicks.keyEvent("F1").isKey(KeyCode.F1)).isTrue();
        assertThat(HintClicks.keyEvent(" F5 ").isKey(KeyCode.F5)).as("spaces around are trimmed").isTrue();
        assertThat(HintClicks.keyEvent("F12").isKey(KeyCode.F12)).isTrue();
    }

    @Test
    void keyEventParsesNamedAndSingleCharKeys() {
        assertThat(HintClicks.keyEvent("Enter").isKey(KeyCode.ENTER)).isTrue();
        assertThat(HintClicks.keyEvent("Esc").isKey(KeyCode.ESCAPE)).isTrue();
        assertThat(HintClicks.keyEvent("Tab").isKey(KeyCode.TAB)).isTrue();
        assertThat(HintClicks.keyEvent("Space").isChar(' ')).isTrue();
        assertThat(HintClicks.keyEvent("d").isChar('d')).isTrue();
        assertThat(HintClicks.keyEvent("?").isChar('?')).isTrue();
        assertThat(HintClicks.keyEvent("+").isChar('+')).isTrue();
        assertThat(HintClicks.keyEvent("F").isChar('F')).as("F alone is the letter").isTrue();
    }

    @Test
    void keyEventRejectsWhatIsNotOneKey() {
        assertThat(HintClicks.keyEvent("+/-")).isNull();
        assertThat(HintClicks.keyEvent("Up/Down")).isNull();
        assertThat(HintClicks.keyEvent(TuiIcons.HINT_SCROLL)).isNull();
        assertThat(HintClicks.keyEvent("F13")).isNull();
        assertThat(HintClicks.keyEvent("")).isNull();
        assertThat(HintClicks.keyEvent(null)).isNull();
    }

    @Test
    void keyPairSplitsTwoKeysAroundASlash() {
        KeyEvent[] zoom = HintClicks.keyPair(" +/- ");
        assertThat(zoom[0].isChar('+')).isTrue();
        assertThat(zoom[1].isChar('-')).isTrue();
        KeyEvent[] fold = HintClicks.keyPair("f/F");
        assertThat(fold[0].isChar('f')).isTrue();
        assertThat(fold[1].isChar('F')).isTrue();
        KeyEvent[] close = HintClicks.keyPair("Esc/c");
        assertThat(close[0].isKey(KeyCode.ESCAPE)).isTrue();
        assertThat(close[1].isChar('c')).isTrue();

        assertThat(HintClicks.keyPair("Up/Down")).as("Up and Down are not keys of a hint").isNull();
        assertThat(HintClicks.keyPair("/")).isNull();
        assertThat(HintClicks.keyPair("a/b/c")).isNull();
        assertThat(HintClicks.keyPair(null)).isNull();
    }

    @Test
    void regionAtResolvesClicksToTheOwningRegion() {
        // two half-open regions [0, 5) and [10, 18) with a gap between them
        int[] startX = { 0, 10 };
        int[] endX = { 5, 18 };
        assertThat(HintClicks.regionAt(startX, endX, 0)).isZero();
        assertThat(HintClicks.regionAt(startX, endX, 4)).isZero();
        assertThat(HintClicks.regionAt(startX, endX, 5)).as("the end is exclusive").isEqualTo(-1);
        assertThat(HintClicks.regionAt(startX, endX, 7)).as("the gap").isEqualTo(-1);
        assertThat(HintClicks.regionAt(startX, endX, 10)).isEqualTo(1);
        assertThat(HintClicks.regionAt(startX, endX, 17)).isEqualTo(1);
        assertThat(HintClicks.regionAt(startX, endX, 18)).isEqualTo(-1);
        assertThat(HintClicks.regionAt(null, endX, 3)).as("nothing captured yet").isEqualTo(-1);
    }

    @Test
    void aClickOnTheDiagramFooterPressesTheKeyUnderIt() {
        List<Span> spans = new ArrayList<>();
        TuiHelper.hint(spans, "Esc", "back");
        TuiHelper.hint(spans, "+/-", "zoom");
        TuiHelper.hint(spans, "Space", "fold");
        TuiHelper.hint(spans, "f/F", "fold/expand all");
        TuiHelper.hint(spans, "Up/Down", "move");
        String line = spans.stream().map(Span::content).reduce("", String::concat);
        HintClicks clicks = new HintClicks();
        clicks.capture(new Rect(0, 9, line.length(), 1), spans);

        assertThat(clicks.keyAt(line.indexOf("back"), 9).isKey(KeyCode.ESCAPE)).isTrue();
        assertThat(clicks.keyAt(line.indexOf("+/-"), 9).isChar('+')).isTrue();
        assertThat(clicks.keyAt(line.indexOf("+/-") + 1, 9)).as("the slash").isNull();
        assertThat(clicks.keyAt(line.indexOf("+/-") + 2, 9).isChar('-')).isTrue();
        assertThat(clicks.keyAt(line.indexOf("zoom"), 9).isChar('+')).as("a one-name label: the first key").isTrue();
        assertThat(clicks.keyAt(line.indexOf("Space"), 9).isChar(' ')).isTrue();
        assertThat(clicks.keyAt(line.indexOf(" fold  "), 9).isChar(' ')).isTrue();
        assertThat(clicks.keyAt(line.indexOf("f/F"), 9).isChar('f')).isTrue();
        assertThat(clicks.keyAt(line.indexOf("f/F") + 2, 9).isChar('F')).isTrue();
        assertThat(clicks.keyAt(line.indexOf("fold/expand"), 9).isChar('f')).isTrue();
        assertThat(clicks.keyAt(line.indexOf("expand all"), 9).isChar('F')).isTrue();
        assertThat(clicks.keyAt(line.indexOf("Up/Down"), 9)).as("not keys of a hint").isNull();
        assertThat(clicks.keyAt(line.indexOf("back"), 8)).as("another row").isNull();

        clicks.clear();
        assertThat(clicks.keyAt(line.indexOf("back"), 9)).as("a footer without hints").isNull();
    }
}
