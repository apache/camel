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
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import org.apache.camel.dsl.jbang.core.commands.tui.SubViewBar.Spec;
import org.apache.camel.dsl.jbang.core.commands.tui.SubViewBar.Toggle;
import org.apache.camel.dsl.jbang.core.commands.tui.SubViewBar.View;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The view bar of a tab: every view shown, the current one marked, a click goes to a view unless it does not apply, and
 * a click on a view setting presses its key.
 */
class SubViewBarTest {

    private final AtomicReference<String> selected = new AtomicReference<>();

    private Spec levels() {
        return new Spec(
                "v", List.of(
                        new View("Architecture", false, true, () -> selected.set("architecture")),
                        new View("Topology · Order intake", true, true, () -> selected.set("topology")),
                        new View("Route", false, false, () -> selected.set("route"))),
                List.of(new Toggle("b", "view", "business"), new Toggle("e", "external", "edges"),
                        new Toggle("m", "metrics", "off")),
                true);
    }

    private static String render(SubViewBar bar, Spec spec, int y) {
        Rect area = new Rect(0, y, 160, 1);
        Buffer buffer = Buffer.empty(new Rect(0, 0, 160, y + 1));
        bar.render(Frame.forTesting(buffer), area, spec);
        String all = TuiTestHelper.bufferToString(buffer);
        String[] rows = all.split("\n");
        return rows[rows.length - 1];
    }

    @Test
    void showsAllViewsTheCurrentOneAndTheSettings() {
        SubViewBar bar = new SubViewBar();
        String line = render(bar, levels(), 3);

        assertThat(line).contains("◇ Architecture", "◆ Topology · Order intake", "◇ Route", " › ");
        assertThat(line.indexOf(" v ")).isBetween(0, line.indexOf("Architecture"));
        // the view settings, with their state
        assertThat(line).contains(" b  view: business ", " e  external: edges ", " m  metrics ○");
    }

    @Test
    void settingsThatDoNotFitEndInAnEllipsis() {
        SubViewBar bar = new SubViewBar();
        Rect area = new Rect(0, 0, 90, 1);
        Buffer buffer = Buffer.empty(area);
        bar.render(Frame.forTesting(buffer), area, levels());
        String line = TuiTestHelper.bufferToString(buffer).split("\n")[0];

        assertThat(line).contains(" b  view: business ").doesNotContain("metrics");
        assertThat(line.stripTrailing()).endsWith("…");
        assertThat(line.length()).isLessThanOrEqualTo(90);
        // all fit on a wide bar: no ellipsis
        assertThat(render(bar, levels(), 0)).doesNotContain("…");
    }

    @Test
    void aClickGoesToAViewOrPressesTheKeyOfASetting() {
        SubViewBar bar = new SubViewBar();
        String line = render(bar, levels(), 3);

        bar.viewAt(line.indexOf("Architecture"), 3).select().run();
        assertThat(selected.get()).isEqualTo("architecture");
        assertThat(bar.viewAt(line.indexOf("Order intake"), 3)).as("the view shown").isNull();
        assertThat(bar.viewAt(line.indexOf("Route"), 3)).as("a view that does not apply").isNull();
        assertThat(bar.viewAt(line.indexOf("Architecture"), 2)).as("another row").isNull();

        assertThat(bar.keyAt(line.indexOf("metrics"), 3)).isEqualTo("m");
        assertThat(bar.keyAt(line.indexOf(" e  external"), 3)).isEqualTo("e");
        assertThat(bar.isOnRow(3)).isTrue();
    }

    @Test
    void peerViewsAreSeparatedByABar() {
        SubViewBar bar = new SubViewBar();
        String line = render(bar, new Spec(
                null, List.of(
                        new View("History", true, true, null), new View("Waterfall", false, true, () -> {
                        })),
                List.of(), false), 0);

        assertThat(line).contains("◆ History", " │ ", "◇ Waterfall").doesNotContain(" › ");
    }
}
