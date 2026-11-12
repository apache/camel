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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26861: the mouse events of the user are recorded to a tape in the mouse commands of TamboUI, and play back as
 * the same events.
 */
class TapeRecorderMouseTest {

    private final TapeRecorder recorder = new TapeRecorder();

    @BeforeEach
    void start() {
        recorder.start(null);
    }

    @Test
    void aPressAndItsReleaseAreAClick() {
        record(MouseEvent.press(MouseButton.LEFT, 3, 4), MouseEvent.release(MouseButton.LEFT, 3, 4),
                MouseEvent.press(MouseButton.RIGHT, 5, 6), MouseEvent.release(MouseButton.RIGHT, 5, 6),
                MouseEvent.press(MouseButton.MIDDLE, 7, 8), MouseEvent.release(MouseButton.MIDDLE, 7, 8));

        assertThat(commands()).containsExactly("Click 3 4", "RightClick 5 6", "MiddleClick 7 8");
    }

    @Test
    void aPressThatMovesBeforeItsReleaseIsADrag() {
        record(MouseEvent.press(MouseButton.LEFT, 2, 10), MouseEvent.drag(MouseButton.LEFT, 2, 11),
                MouseEvent.drag(MouseButton.LEFT, 2, 12), MouseEvent.release(MouseButton.LEFT, 2, 12));

        // the pause between the steps comes from how long the drag took
        assertThat(commands()).singleElement().asString().matches("Drag(@\\d+ms)? 2 10 2 12");
    }

    @Test
    void theWheelIsCountedAtItsCell() {
        record(MouseEvent.scrollDown(4, 5), MouseEvent.scrollDown(4, 5), MouseEvent.scrollDown(4, 5),
                MouseEvent.scrollUp(4, 5), MouseEvent.scrollLeft(1, 1), MouseEvent.scrollRight(1, 1),
                MouseEvent.scrollRight(1, 2));

        assertThat(commands()).containsExactly(
                "ScrollDown 3 4 5", "ScrollUp 4 5", "ScrollLeft 1 1", "ScrollRight 1 1", "ScrollRight 1 2");
    }

    @Test
    void movingTheMouseOnlyHoversAndIsNotRecorded() {
        record(MouseEvent.move(1, 1), MouseEvent.move(2, 2), MouseEvent.release(MouseButton.LEFT, 2, 2),
                MouseEvent.drag(MouseButton.LEFT, 3, 3));

        assertThat(commands()).isEmpty();
        assertThat(recorder.stop()).doesNotContain("Mouse positions");
    }

    @Test
    void aKeyWhileTheButtonIsHeldSplitsTheClickInMouseDownAndMouseUp() {
        record(MouseEvent.press(MouseButton.LEFT, 3, 4));
        recorder.recordKey("Enter");
        record(MouseEvent.release(MouseButton.LEFT, 3, 4));

        assertThat(commands()).containsExactly("MouseDown 3 4", "Enter", "MouseUp 3 4");
    }

    @Test
    void theTapeSaysTheScreenSizeOfItsMousePositions() {
        record(MouseEvent.press(MouseButton.LEFT, 3, 4), MouseEvent.release(MouseButton.LEFT, 3, 4));

        assertThat(recorder.stop()).contains(
                "Set Theme \"Aardvark Blue\"\n\n# Mouse positions are cells of a 120x32 screen: play the tape with "
                                             + "--record-size=120x32\n");
    }

    @Test
    void theRecordedMousePlaysAsTheSameEvents() {
        List<MouseEvent> events = List.of(
                MouseEvent.press(MouseButton.LEFT, 3, 4), MouseEvent.release(MouseButton.LEFT, 3, 4),
                MouseEvent.press(MouseButton.RIGHT, 5, 6), MouseEvent.release(MouseButton.RIGHT, 5, 6),
                MouseEvent.scrollDown(7, 8), MouseEvent.scrollDown(7, 8));
        record(events.toArray(new MouseEvent[0]));

        List<String> warnings = new ArrayList<>();
        List<MouseEvent> replayed = new ArrayList<>();
        for (TapePlayer.Step step : new TapePlayer(warnings::add).parse(recorder.stop().lines().toList())) {
            if (step instanceof TapePlayer.MouseStep m) {
                replayed.add(m.event());
            }
        }

        assertThat(replayed).isEqualTo(events);
        assertThat(warnings).isEmpty();
    }

    @Test
    void aClickOnAKeyHintIsRecordedAsTheKey() {
        RecordingManager manager = new RecordingManager(new CaptionOverlay());
        manager.init(false);
        manager.updateBuffer(Buffer.empty(new Rect(0, 0, 80, 24)));
        manager.startTapeRecording(null);
        KeyEvent f2 = KeyEvent.ofKey(KeyCode.F2, KeyModifiers.NONE);

        // the footer: a click on the F2 hint presses F2
        manager.recordMouse(MouseEvent.press(MouseButton.LEFT, 10, 23), false);
        manager.pressedByClick(() -> {
            manager.recordKey(f2, false);
            return true;
        });
        manager.recordMouse(MouseEvent.release(MouseButton.LEFT, 10, 23), false);
        // a click on a tab is a click; the clicks a tape plays are not recorded again
        manager.recordMouse(MouseEvent.press(MouseButton.LEFT, 4, 1), false);
        manager.recordMouse(MouseEvent.release(MouseButton.LEFT, 4, 1), false);
        manager.recordMouse(MouseEvent.press(MouseButton.LEFT, 9, 1), true);
        manager.recordMouse(MouseEvent.release(MouseButton.LEFT, 9, 1), true);

        String tape = manager.getTapeRecorder().stop();
        assertThat(tape).endsWith("F2\nClick 4 1\n").contains("80x24 screen");
    }

    @Test
    void closingTheMenuWithAClickLeavesTheClickOut() {
        recorder.recordKey("F2");
        int menu = recorder.preKeyPosition();
        record(MouseEvent.press(MouseButton.LEFT, 5, 5));
        // the click picked an entry, which closed the menu: what was done in the menu is not recorded
        recorder.trimTo(menu);
        record(MouseEvent.release(MouseButton.LEFT, 5, 5));

        assertThat(commands()).isEmpty();
    }

    private void record(MouseEvent... events) {
        for (MouseEvent event : events) {
            recorder.recordMouse(event, 120, 32);
        }
    }

    /** The commands of the tape after its Set lines (and the comment on the screen size), with no Sleep. */
    private List<String> commands() {
        return recorder.stop().lines()
                .filter(l -> !l.isBlank() && !l.startsWith("Set ") && !l.startsWith("#") && !l.startsWith("Sleep "))
                .toList();
    }
}
