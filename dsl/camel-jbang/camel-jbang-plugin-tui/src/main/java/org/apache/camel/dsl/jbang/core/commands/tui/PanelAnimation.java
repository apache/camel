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

/**
 * Animated panel height controller for the shell and AI panels. Cycles through preset percentage sizes (25/50/75/100%)
 * with smooth deceleration animation.
 */
class PanelAnimation {

    private static final int[] CYCLE_PERCENTS = { 25, 50, 75, 100 };

    private int panelHeight = -1;
    private int targetPanelHeight = -1;
    private int cycleIndex = 1;

    int panelHeight() {
        return panelHeight;
    }

    int cyclePercent() {
        return CYCLE_PERCENTS[cycleIndex];
    }

    boolean isAnimating() {
        return targetPanelHeight >= 0 && panelHeight != targetPanelHeight;
    }

    void tickAnimation() {
        if (targetPanelHeight >= 0 && panelHeight != targetPanelHeight) {
            int remaining = targetPanelHeight - panelHeight;
            int step = Math.max(2, Math.abs(remaining) / 2);
            if (remaining > 0) {
                panelHeight = Math.min(panelHeight + step, targetPanelHeight);
            } else {
                panelHeight = Math.max(panelHeight - step, targetPanelHeight);
            }
        }
    }

    void initHeight(int contentHeight) {
        if (panelHeight < 0) {
            panelHeight = contentHeight * CYCLE_PERCENTS[cycleIndex] / 100;
            targetPanelHeight = panelHeight;
        }
    }

    void cycleHeight(int contentHeight) {
        cycleIndex = (cycleIndex + 1) % CYCLE_PERCENTS.length;
        targetPanelHeight = contentHeight * CYCLE_PERCENTS[cycleIndex] / 100;
        if (panelHeight < 0) {
            panelHeight = targetPanelHeight;
        }
    }

    void setPanelHeight(int height) {
        panelHeight = height;
        targetPanelHeight = height;
    }
}
