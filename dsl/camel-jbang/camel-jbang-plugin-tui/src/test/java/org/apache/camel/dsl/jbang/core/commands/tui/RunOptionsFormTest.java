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
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link RunOptionsForm} through key events to verify the JFR and chaos checkbox rows toggle {@code --jfr} and
 * {@code --chaos} into {@link RunOptionsForm#buildArgs()}, and that inserting those rows did not break navigation past
 * the other checkbox rows or the down-wrap back to row 0.
 */
class RunOptionsFormTest {

    private static KeyEvent key(KeyCode code) {
        return KeyEvent.ofKey(code, KeyModifiers.NONE);
    }

    private static RunOptionsForm openForm() {
        RunOptionsForm form = new RunOptionsForm();
        form.open(null, null, false);
        return form;
    }

    @Test
    void jfrOffByDefaultOmitsFlag() {
        RunOptionsForm form = openForm();

        assertThat(form.buildArgs()).doesNotContain("--jfr");
    }

    @Test
    void togglingJfrRowAddsJfrFlag() {
        RunOptionsForm form = openForm();

        // Name, Runtime, Profile, Port, Init heap, Max heap, Max, Console, Dev, Observe, Trace, Stub, OTel Agent, JFR
        for (int i = 0; i < 13; i++) {
            form.handleKeyEvent(key(KeyCode.DOWN));
        }
        form.handleKeyEvent(KeyEvent.ofChar(' '));

        assertThat(form.buildArgs()).contains("--jfr");
    }

    @Test
    void togglingJfrRowTwiceOmitsFlag() {
        RunOptionsForm form = openForm();

        for (int i = 0; i < 13; i++) {
            form.handleKeyEvent(key(KeyCode.DOWN));
        }
        form.handleKeyEvent(KeyEvent.ofChar(' '));
        form.handleKeyEvent(KeyEvent.ofChar(' '));

        assertThat(form.buildArgs()).doesNotContain("--jfr");
    }

    @Test
    void downNavigationWrapsFromChaosRowBackToNameRow() {
        RunOptionsForm form = openForm();

        // 15 rows total (0=Name .. 13=JFR, 14=Chaos); 15 downs from row 0 wraps back to row 0 (Name).
        for (int i = 0; i < 15; i++) {
            form.handleKeyEvent(key(KeyCode.DOWN));
        }
        form.handleKeyEvent(KeyEvent.ofChar('x'));

        assertThat(form.name()).isEqualTo("x");
    }

    @Test
    void otelAgentCheckboxStillTogglesAfterJfrRowInsertion() {
        RunOptionsForm form = openForm();

        // Name, Runtime, Profile, Port, Init heap, Max heap, Max, Console, Dev, Observe, Trace, Stub, OTel Agent
        for (int i = 0; i < 12; i++) {
            form.handleKeyEvent(key(KeyCode.DOWN));
        }
        form.handleKeyEvent(KeyEvent.ofChar(' '));

        List<String> args = form.buildArgs();
        assertThat(args).contains("--open-telemetry-agent");
        assertThat(args).doesNotContain("--jfr");
    }

    @Test
    void chaosOffByDefaultOmitsFlag() {
        RunOptionsForm form = openForm();

        assertThat(form.buildArgs()).doesNotContain("--chaos");
        assertThat(form.isChaosEnabled()).isFalse();
    }

    @Test
    void togglingChaosRowAddsChaosFlag() {
        RunOptionsForm form = openForm();

        // up from Name wraps to the last row, Chaos
        form.handleKeyEvent(key(KeyCode.UP));
        form.handleKeyEvent(KeyEvent.ofChar(' '));

        assertThat(form.buildArgs()).contains("--chaos").doesNotContain("--jfr");
        assertThat(form.isChaosEnabled()).isTrue();
    }

    @Test
    void chaosNotPassedWithProfileProd() {
        RunOptionsForm form = openForm();

        form.handleKeyEvent(key(KeyCode.UP));
        form.handleKeyEvent(KeyEvent.ofChar(' '));
        // Profile is row 2: cycle it from dev to prod
        form.handleKeyEvent(key(KeyCode.DOWN));
        form.handleKeyEvent(key(KeyCode.DOWN));
        form.handleKeyEvent(key(KeyCode.DOWN));
        form.handleKeyEvent(KeyEvent.ofChar(' '));

        assertThat(form.buildArgs()).contains("--profile=prod").doesNotContain("--chaos");
        assertThat(renderPopupRows(form)).anyMatch(row -> row.contains("Chaos testing (not available with profile prod)"));
    }

    @Test
    void chaosRowCannotBeToggledWithProfileProd() {
        RunOptionsForm form = openForm();

        // Profile is row 2: cycle it from dev to prod, then go up to the Chaos row and try to tick it
        form.handleKeyEvent(key(KeyCode.DOWN));
        form.handleKeyEvent(key(KeyCode.DOWN));
        form.handleKeyEvent(KeyEvent.ofChar(' '));
        for (int i = 0; i < 3; i++) {
            form.handleKeyEvent(key(KeyCode.UP));
        }
        form.handleKeyEvent(KeyEvent.ofChar(' '));
        // back to dev: the box was not ticked while prod was selected
        for (int i = 0; i < 3; i++) {
            form.handleKeyEvent(key(KeyCode.DOWN));
        }
        form.handleKeyEvent(KeyEvent.ofChar(' '));

        assertThat(form.buildArgs()).contains("--profile=dev").doesNotContain("--chaos");
    }

    @Test
    void optionsPageHasNoBlankRows() {
        RunOptionsForm form = openForm();
        // no app holds a port, so the form shows no port warning that would take a row of its own
        form.setIntegrations(List::of);

        List<String> inner = renderPopupRows(form);

        // Name .. Chaos: one row each, nothing left empty below the last checkbox
        assertThat(inner).hasSize(15 + (form.portWarning(MonotonicClock.millis()) != null ? 1 : 0));
        assertThat(inner).noneMatch(String::isBlank);
    }

    /** The rows inside the popup border (between the top and bottom edge), with the side borders cut off. */
    private static List<String> renderPopupRows(RunOptionsForm form) {
        Rect area = new Rect(0, 0, 100, 40);
        Buffer buffer = Buffer.empty(area);
        Theme.resetForTesting();
        form.render(Frame.forTesting(buffer), area);
        String[] lines = HealthTabRenderTest.bufferToString(buffer).split("\n");
        List<String> inner = new ArrayList<>();
        boolean in = false;
        for (String line : lines) {
            if (line.contains("╭")) {
                in = true;
            } else if (line.contains("╰")) {
                break;
            } else if (in) {
                int left = line.indexOf('│');
                int right = line.lastIndexOf('│');
                inner.add(line.substring(left + 1, right));
            }
        }
        return inner;
    }
}
