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

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link RunOptionsForm} through key events to verify the JFR checkbox row toggles {@code --jfr} into
 * {@link RunOptionsForm#buildArgs()}, and that inserting that row did not break navigation past the other checkbox rows
 * or the down-wrap back to row 0.
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
    void downNavigationWrapsFromJfrRowBackToNameRow() {
        RunOptionsForm form = openForm();

        // 14 rows total (0=Name .. 13=JFR); 14 downs from row 0 wraps back to row 0 (Name).
        for (int i = 0; i < 14; i++) {
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
}
