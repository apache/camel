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
import java.util.concurrent.atomic.AtomicLong;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaptionOverlayTest {

    // a family: three emoji joined by ZWJs, eight chars that take two columns
    private static final String FAMILY = "👨\u200D👩\u200D👧";

    @Test
    void anInlineCaptionInWideCharactersIsNotCut() {
        CaptionOverlay caption = new CaptionOverlay();
        caption.openInline();
        for (String ch : List.of("日", "本", "語")) {
            caption.handleKeyEvent(KeyEvent.ofChar(ch.codePointAt(0)));
        }
        Rect area = new Rect(0, 0, 60, 10);
        Buffer buffer = Buffer.empty(area);

        caption.render(Frame.forTesting(buffer), area);

        // the caret after the text is still in the caption box
        assertThat(TuiTestHelper.bufferToString(buffer)).contains("█");
    }

    @Test
    void backspaceInAnInlineCaptionDeletesAWholeEmoji() {
        CaptionOverlay caption = new CaptionOverlay();
        caption.openInline();
        caption.handleKeyEvent(KeyEvent.ofChar('a'));
        caption.handleKeyEvent(KeyEvent.ofChar(0x1F600));
        caption.handleKeyEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));
        caption.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));

        assertThat(caption.captionTextForTesting()).isEqualTo("a");
    }

    @Test
    void backspaceInAnInlineCaptionDeletesAWholeEmojiOfSeveralCodePoints() {
        CaptionOverlay caption = new CaptionOverlay();
        caption.openInline();
        caption.handleKeyEvent(KeyEvent.ofChar('a'));
        // a family arrives as five keys, a code point each
        FAMILY.codePoints().forEach(cp -> caption.handleKeyEvent(KeyEvent.ofChar(cp)));
        caption.handleKeyEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));
        caption.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));

        assertThat(caption.captionTextForTesting()).isEqualTo("a");
    }

    @Test
    void aCaptionIsTypedAWholeEmojiAtATime() {
        AtomicLong now = new AtomicLong(1_000);
        CaptionOverlay caption = new CaptionOverlay(now::get);
        caption.showCaption("a" + FAMILY + "b", 0);
        Rect area = new Rect(0, 0, 40, 5);

        // a character every 50 ms: after 100 ms the a and the whole family, not the first emoji of it
        now.addAndGet(100);
        Buffer buffer = Buffer.empty(area);
        caption.render(Frame.forTesting(buffer), area);

        assertThat(TuiTestHelper.bufferToString(buffer)).contains("a" + FAMILY).doesNotContain("b");
    }
}
