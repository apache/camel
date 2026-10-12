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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaptionOverlayTest {

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
}
