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

import dev.tamboui.layout.Rect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests how the content area is split between the tab and the shell/AI panel for the Panel Position (bottom/top) and
 * Panel Space (move/overlay) settings.
 */
class CamelMonitorPanelLayoutTest {

    private static final Rect CONTENT = new Rect(0, 3, 120, 30);

    @Test
    void bottomMoveTakesRowsFromTheTab() {
        Rect[] layout = CamelMonitor.panelLayout(CONTENT, 10, false, false);
        assertThat(layout[0]).isEqualTo(new Rect(0, 3, 120, 20));
        assertThat(layout[1]).isEqualTo(new Rect(0, 23, 120, 10));
    }

    @Test
    void topMovePushesTheTabDown() {
        Rect[] layout = CamelMonitor.panelLayout(CONTENT, 10, true, false);
        assertThat(layout[1]).isEqualTo(new Rect(0, 3, 120, 10));
        assertThat(layout[0]).isEqualTo(new Rect(0, 13, 120, 20));
    }

    @Test
    void overlayKeepsTheFullTabArea() {
        Rect[] bottom = CamelMonitor.panelLayout(CONTENT, 10, false, true);
        assertThat(bottom[0]).isEqualTo(CONTENT);
        assertThat(bottom[1]).isEqualTo(new Rect(0, 23, 120, 10));

        Rect[] top = CamelMonitor.panelLayout(CONTENT, 10, true, true);
        assertThat(top[0]).isEqualTo(CONTENT);
        assertThat(top[1]).isEqualTo(new Rect(0, 3, 120, 10));
    }

    @Test
    void fullHeightPanelLeavesNoTabArea() {
        Rect[] layout = CamelMonitor.panelLayout(CONTENT, 30, true, false);
        assertThat(layout[0].height()).isZero();
        assertThat(layout[1]).isEqualTo(CONTENT);

        Rect[] oversized = CamelMonitor.panelLayout(CONTENT, 99, false, true);
        assertThat(oversized[1]).isEqualTo(CONTENT);
    }

    @Test
    void pinnedLogKeepsItsHeightBelowATopPanelWhenThereIsRoom() {
        assertThat(CamelMonitor.pinnedLogHeight(30, 10, 7)).isEqualTo(7);
    }

    @Test
    void pinnedLogShrinksSoPanelAndSomeTabRemain() {
        // 30 rows: 20 for the panel, 3 kept for the tab, leaves 7 for the pin even if 10 were requested
        assertThat(CamelMonitor.pinnedLogHeight(30, 20, 10)).isEqualTo(7);
        assertThat(CamelMonitor.pinnedLogHeight(30, 28, 10)).isZero();
        assertThat(CamelMonitor.pinnedLogHeight(30, 30, 10)).isZero();
    }
}
