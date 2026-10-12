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

class AiLogPopupTest {

    @Test
    void pageUpMovesAtOnceAfterPageDownWentPastTheEnd() {
        StringBuilder json = new StringBuilder("{");
        for (int i = 1; i <= 30; i++) {
            json.append(i > 1 ? "," : "").append("\"key").append(i).append("\":").append(i);
        }
        json.append('}');
        AiLogPopup popup = new AiLogPopup();
        popup.setActivityLog(() -> List.of(
                new AiPanel.LogEntry("10:00:00", AiPanel.LogLevel.RESULT, "tui_get_state", json.toString())));
        popup.open();
        Rect area = new Rect(0, 0, 80, 30);

        for (int i = 0; i < 20; i++) {
            popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.PAGE_DOWN));
        }
        assertThat(render(popup, area)).contains("\"key30\"");
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.PAGE_UP));

        assertThat(render(popup, area)).doesNotContain("\"key30\"");
    }

    private static String render(AiLogPopup popup, Rect area) {
        Buffer buffer = Buffer.empty(area);
        popup.render(Frame.forTesting(buffer), area);
        return TuiTestHelper.bufferToString(buffer);
    }
}
