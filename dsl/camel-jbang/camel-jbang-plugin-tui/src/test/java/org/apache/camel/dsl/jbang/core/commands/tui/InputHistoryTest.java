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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InputHistoryTest {

    @Test
    void thePopupHasAFrame() {
        InputHistory history = new InputHistory();
        history.add("select * from orders");
        history.add("select count(*) from orders");
        history.showPopup();
        Rect area = new Rect(0, 0, 60, 12);
        Buffer buffer = Buffer.empty(area);

        history.renderPopup(Frame.forTesting(buffer), area, "SQL History");

        String rendered = TuiTestHelper.bufferToString(buffer);
        assertThat(rendered).contains("╭", "╮", "╰", "╯", "SQL History", "select count(*) from orders");
    }
}
