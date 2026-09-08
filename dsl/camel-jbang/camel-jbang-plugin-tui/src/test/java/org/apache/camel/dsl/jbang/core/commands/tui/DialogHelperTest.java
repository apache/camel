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
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widgets.input.TextInputState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the shared dialog building blocks in {@link DialogHelper} and the hint helpers in {@link TuiHelper}.
 */
class DialogHelperTest {

    private static final Rect AREA = new Rect(0, 0, 120, 40);

    @Test
    void centeredPlacesDialogInUpperThird() {
        Rect r = DialogHelper.centered(AREA, 40, 10);
        assertThat(r.x()).isEqualTo(40);
        assertThat(r.y()).isEqualTo(10);
        assertThat(r.width()).isEqualTo(40);
        assertThat(r.height()).isEqualTo(10);
    }

    @Test
    void centeredClampsToArea() {
        Rect r = DialogHelper.centered(new Rect(5, 5, 20, 8), 40, 10);
        assertThat(r.x()).isEqualTo(5);
        assertThat(r.y()).isEqualTo(5);
        assertThat(r.width()).isEqualTo(20);
        assertThat(r.height()).isEqualTo(8);
    }

    @Test
    void clampWidthKeepsMarginAndBounds() {
        assertThat(DialogHelper.clampWidth(AREA, 34, 20)).isEqualTo(34);
        assertThat(DialogHelper.clampWidth(AREA, 34, 60)).isEqualTo(60);
        assertThat(DialogHelper.clampWidth(AREA, 34, 500)).isEqualTo(116);
        // narrower than the minimum: shrink to fit rather than overflow
        assertThat(DialogHelper.clampWidth(new Rect(0, 0, 30, 10), 34, 60)).isEqualTo(28);
    }

    @Test
    void confirmDialogShowsTitleMessageAndFooterStyleHints() {
        Buffer buffer = Buffer.empty(AREA);
        Frame frame = Frame.forTesting(buffer);

        Rect popup = DialogHelper.renderConfirm(frame, AREA, "Confirm Quit", "Quit the TUI?", false);

        String rendered = HealthTabRenderTest.bufferToString(buffer);
        assertThat(rendered).contains(" Confirm Quit ");
        assertThat(rendered).contains("Quit the TUI?");
        assertThat(rendered).contains(" Enter  confirm");
        assertThat(rendered).contains(" Esc  cancel");
        assertThat(popup.height()).isEqualTo(DialogHelper.CONFIRM_HEIGHT);
        assertThat(popup.width()).isGreaterThanOrEqualTo(34);
    }

    @Test
    void confirmDialogCanUseCustomAcceptKey() {
        Buffer buffer = Buffer.empty(AREA);
        Frame frame = Frame.forTesting(buffer);

        DialogHelper.renderConfirm(frame, AREA, "Delete file?", "Delete foo.yaml?", true, "y", "delete");

        String rendered = HealthTabRenderTest.bufferToString(buffer);
        assertThat(rendered).contains(" y  delete");
        assertThat(rendered).contains(" Esc  cancel");
        assertThat(rendered).doesNotContain("Enter");
    }

    @Test
    void inputDialogShowsTitleAndPlaceholder() {
        Buffer buffer = Buffer.empty(AREA);
        Frame frame = Frame.forTesting(buffer);

        Rect popup = DialogHelper.renderInputDialog(frame, AREA, "New File", new TextInputState(), "name");

        String rendered = HealthTabRenderTest.bufferToString(buffer);
        assertThat(rendered).contains(" New File ");
        assertThat(rendered).contains("name");
        assertThat(popup.height()).isEqualTo(DialogHelper.INPUT_HEIGHT);
    }

    @Test
    void hintLineMatchesFooterHints() {
        Line line = TuiHelper.hintLine("Enter", "confirm", "Esc", "cancel");
        assertThat(line.spans()).extracting(Span::content)
                .containsExactly(" Enter ", " confirm  ", " Esc ", " cancel");
    }

    @Test
    void hintLineRejectsOddArguments() {
        assertThatThrownBy(() -> TuiHelper.hintLine("Enter"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
