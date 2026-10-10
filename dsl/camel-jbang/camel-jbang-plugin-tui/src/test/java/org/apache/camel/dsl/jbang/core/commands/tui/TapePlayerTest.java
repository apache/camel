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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class TapePlayerTest {

    private final List<String> warnings = new ArrayList<>();
    private final TapePlayer player = new TapePlayer(warnings::add);

    @Test
    void parsesKeysWithModifiers() {
        assertThat(TapePlayer.parseKey("F8").code()).isEqualTo(KeyCode.F8);
        KeyEvent shiftF8 = TapePlayer.parseKey("Shift+F8");
        assertThat(shiftF8.code()).isEqualTo(KeyCode.F8);
        assertThat(shiftF8.hasShift()).isTrue();
        KeyEvent ctrlT = TapePlayer.parseKey("Ctrl+t");
        assertThat(ctrlT.isChar('t')).isTrue();
        assertThat(ctrlT.hasCtrl()).isTrue();
        assertThat(TapePlayer.parseKey("Alt+x").hasAlt()).isTrue();
        assertThat(TapePlayer.parseKey("Shift+Tab").code()).isEqualTo(KeyCode.TAB);
        assertThat(TapePlayer.parseKey("Escape").code()).isEqualTo(KeyCode.ESCAPE);
        assertThat(TapePlayer.parseKey("PageDown").code()).isEqualTo(KeyCode.PAGE_DOWN);
    }

    @Test
    void doesNotTakeABareLetterOrAnUnknownNameForAKey() {
        assertThat(TapePlayer.parseKey("q")).isNull();
        assertThat(TapePlayer.parseKey("F13")).isNull();
        assertThat(TapePlayer.parseKey("Hyper+x")).isNull();
    }

    @Test
    void repeatsAKeyWithACount() {
        List<TapePlayer.Step> steps = player.parse(List.of("Down 3", "Tab", "Tab 2"));

        assertThat(keys(steps)).extracting(KeyEvent::code)
                .containsExactly(KeyCode.DOWN, KeyCode.DOWN, KeyCode.DOWN, KeyCode.TAB, KeyCode.TAB, KeyCode.TAB);
        assertThat(warnings).isEmpty();
    }

    @Test
    void typesTextWithItsSpeedAndKeepsAtSigns() {
        List<TapePlayer.Step> steps = player.parse(List.of("Type@100ms \"a@b\""));

        assertThat(keys(steps)).extracting(KeyEvent::string).containsExactly("a", "@", "b");
        assertThat(steps).filteredOn(s -> s instanceof TapePlayer.SleepStep)
                .extracting(s -> ((TapePlayer.SleepStep) s).millis())
                .containsOnly(100L);
    }

    @Test
    void parsesDurationsAsInVhs() {
        assertThat(TapePlayer.parseDuration("500ms")).isEqualTo(500);
        assertThat(TapePlayer.parseDuration("2s")).isEqualTo(2000);
        assertThat(TapePlayer.parseDuration("1.5s")).isEqualTo(1500);
        assertThat(TapePlayer.parseDuration("3")).isEqualTo(3000);
    }

    @Test
    void parsesWaitWithItsTimeout() {
        TapePlayer.WaitStep wait = (TapePlayer.WaitStep) player.parse(List.of("Wait+Screen@2s /Ready/")).get(0);

        assertThat(wait.pattern().pattern()).isEqualTo("Ready");
        assertThat(wait.wholeScreen()).isTrue();
        assertThat(wait.timeoutMillis()).isEqualTo(2000);
    }

    @Test
    void parsesTheTuiCommands() {
        List<TapePlayer.Step> steps = player.parse(List.of(
                "Caption@5s \"Fix with AI\\nShift+F8\"",
                "Tab \"Errors\"",
                "Integration orders",
                "Action \"show-keystrokes\"",
                "Theme \"dracula\"",
                "ClearDrawing"));

        assertThat(steps).hasSize(6).allMatch(s -> s instanceof TapePlayer.ToolStep);
        TapePlayer.ToolStep caption = (TapePlayer.ToolStep) steps.get(0);
        assertThat(caption.name()).isEqualTo("tui_show_caption");
        assertThat(caption.args()).containsEntry("text", "Fix with AI\nShift+F8").containsEntry("duration", 5);
        assertThat(((TapePlayer.ToolStep) steps.get(1)).args()).containsEntry("tab", "Errors");
        assertThat(((TapePlayer.ToolStep) steps.get(2)).args()).containsEntry("integration", "orders");
        assertThat(((TapePlayer.ToolStep) steps.get(3)).name()).isEqualTo("tui_action");
        assertThat(((TapePlayer.ToolStep) steps.get(4)).name()).isEqualTo("tui_set_theme");
        assertThat(((TapePlayer.ToolStep) steps.get(5)).name()).isEqualTo("tui_draw_clear");
        assertThat(warnings).isEmpty();
    }

    @Test
    void reportsAndSkipsWhatItDoesNotKnow() {
        List<TapePlayer.Step> steps = player.parse(List.of(
                "Set FontSize 32", "Output demo.gif", "Frobnicate", "q", "Hide", "Wait /[unclosed/", "Enter"));

        assertThat(keys(steps)).extracting(KeyEvent::code).containsExactly(KeyCode.ENTER);
        assertThat(warnings).hasSize(4);
        assertThat(warnings.get(0)).contains("Unknown tape command: Frobnicate");
        assertThat(warnings.get(1)).contains("Unknown tape command: q");
        assertThat(warnings.get(2)).contains("Hide and Show are not supported");
        assertThat(warnings.get(3)).contains("Invalid regex");
    }

    @Test
    void includesAnotherTapeAndReportsATapeThatIncludesItself(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("main.tape"), "Source common.tape\nSource common.tape\nSource loop.tape\nEnter\n");
        Files.writeString(dir.resolve("common.tape"), "Escape\n");
        Files.writeString(dir.resolve("loop.tape"), "Tab\nSource ../" + dir.getFileName() + "/main.tape\n");

        List<TapePlayer.Step> steps = player.parse(dir.resolve("main.tape"));

        assertThat(keys(steps)).extracting(KeyEvent::code)
                .containsExactly(KeyCode.ESCAPE, KeyCode.ESCAPE, KeyCode.TAB, KeyCode.ENTER);
        assertThat(warnings).singleElement().asString().startsWith("The tape includes itself");
    }

    @Test
    void waitMatchesALineOrTheWholeScreen() {
        String screen = "Loading\nReady  ";
        TapePlayer.WaitStep line = new TapePlayer.WaitStep(Pattern.compile("Loading\\s*\\nReady"), false, 0, "");
        TapePlayer.WaitStep whole = new TapePlayer.WaitStep(Pattern.compile("Loading\\s*\\nReady"), true, 0, "");

        assertThat(TapePlayer.matches(line, screen)).isFalse();
        assertThat(TapePlayer.matches(whole, screen)).isTrue();
        assertThat(TapePlayer.matches(whole, null)).isFalse();
    }

    @Test
    void playsKeysWaitsForTheScreenHighlightsAndQuits() throws Exception {
        AtomicReference<String> screen = new AtomicReference<>("Loading");
        List<KeyEvent> pressed = new ArrayList<>();
        List<String> tools = new ArrayList<>();
        AtomicBoolean quit = new AtomicBoolean();
        TapePlayer.Driver driver = new TapePlayer.Driver() {
            @Override
            public void key(KeyEvent event) {
                pressed.add(event);
                if (event.code() == KeyCode.F5) {
                    screen.set("Errors\n Shift+F8  fix with AI");
                }
            }

            @Override
            public String screen() {
                return screen.get();
            }

            @Override
            public String tool(String name, Map<String, Object> args) {
                tools.add(name + " " + args);
                if ("tui_locate".equals(name)) {
                    return "{\"matches\":[{\"x\":1,\"y\":1,\"width\":8,\"height\":1,\"text\":\"Shift+F8\"}]}";
                }
                return "ok";
            }

            @Override
            public void quit() {
                quit.set(true);
            }
        };

        player.play(player.parse(List.of(
                "F5",
                "Wait@2s /fix with AI/",
                "Highlight \"Shift+F8\"",
                "Shift+F8")), driver);

        assertThat(pressed).extracting(KeyEvent::code).containsExactly(KeyCode.F5, KeyCode.F8);
        assertThat(pressed.get(1).hasShift()).isTrue();
        assertThat(tools).hasSize(2);
        assertThat(tools.get(0)).startsWith("tui_locate");
        assertThat(tools.get(1)).startsWith("tui_draw_shape").contains("x=1").contains("y=1").contains("width=8");
        assertThat(quit).isTrue();
        assertThat(warnings).isEmpty();
    }

    @Test
    void reportsAWaitThatTimesOut() throws Exception {
        TapePlayer.Driver driver = new TapePlayer.Driver() {
            @Override
            public void key(KeyEvent event) {
            }

            @Override
            public String screen() {
                return "Loading";
            }

            @Override
            public String tool(String name, Map<String, Object> args) {
                return "ok";
            }

            @Override
            public void quit() {
            }
        };

        player.play(player.parse(List.of("Wait@200ms /Ready/")), driver);

        assertThat(warnings).singleElement().asString().startsWith("Timed out after 200ms");
    }

    @Test
    void screenTextLeavesOutTheContinuationOfWideCharacters() {
        Buffer buffer = Buffer.empty(new Rect(0, 0, 4, 2));
        buffer.setString(0, 0, "世a", Style.EMPTY);
        buffer.setString(0, 1, "ok", Style.EMPTY);

        assertThat(TapePlayer.screenText(buffer)).isEqualTo("世a \nok  ");
    }

    private static List<KeyEvent> keys(List<TapePlayer.Step> steps) {
        List<KeyEvent> keys = new ArrayList<>();
        for (TapePlayer.Step step : steps) {
            if (step instanceof TapePlayer.KeyStep k) {
                keys.add(k.event());
            }
        }
        return keys;
    }
}
