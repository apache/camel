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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

@Isolated("Previews themes, which is global state")
class ThemePopupTest {

    @BeforeEach
    @AfterEach
    void resetTheme() {
        Theme.resetForTesting();
    }

    @Test
    void theSelectedThemeIsShownOnAShortTerminal() {
        ThemePopup popup = new ThemePopup();
        popup.open();
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.END));
        Rect area = new Rect(0, 0, 60, 14);
        Buffer buffer = Buffer.empty(area);

        popup.render(Frame.forTesting(buffer), area);

        List<ThemeMode> light = ThemeMode.lightThemes();
        assertThat(TuiTestHelper.bufferToString(buffer)).contains(light.get(light.size() - 1).label());
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    }
}
