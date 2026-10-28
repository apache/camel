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
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Flow panel of the Architecture view goes where the diagram leaves room, so it does not hide a capability box.
 */
class ArchitectureFlowPanelTest {

    private final Rect area = new Rect(0, 0, 100, 30);

    @Test
    void theBottomRightIsUsedWhenItIsFree() {
        Frame frame = Frame.forTesting(Buffer.empty(area));

        assertThat(ArchitectureView.freeCorner(frame, area, 30, 8)).isEqualTo(new Rect(68, 21, 30, 8));
    }

    @Test
    void aBoxInTheBottomRightMovesThePanelToAFreeCorner() {
        Buffer buffer = Buffer.empty(area);
        // a capability box in the bottom right
        buffer.setString(75, 24, "Customer Notification", Style.EMPTY);
        Frame frame = Frame.forTesting(buffer);

        assertThat(ArchitectureView.freeCorner(frame, area, 30, 8)).isEqualTo(new Rect(2, 21, 30, 8));
    }
}
