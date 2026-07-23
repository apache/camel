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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that an idle tick does not force a redraw while any active animation still does. This is the core of the fix
 * that stopped the TUI from rebuilding the whole frame on every tick (~25 times/second at the default 40ms tick rate)
 * even when nothing on screen was changing.
 */
class CamelMonitorTickRedrawTest {

    @Test
    void idleTickDoesNotRedraw() {
        // Nothing is animating: the tick must NOT request a redraw. The periodic data refresh
        // (handled separately) is the only redraw driver while idle.
        assertFalse(CamelMonitor.needsAnimationRedraw(
                false, false, false, false, false, false, false),
                "an idle tick must not force a redraw");
    }

    @Test
    void eachAnimationSourceForcesRedraw() {
        // Every animation runs faster than the data-refresh cadence, so each one alone must
        // keep the frame updating on every tick.
        assertTrue(only(0), "open shell panel must redraw");
        assertTrue(only(1), "open AI panel must redraw");
        assertTrue(only(2), "sliding pinned-log panel must redraw");
        assertTrue(only(3), "canvas animation must redraw");
        assertTrue(only(4), "typing/fading caption must redraw");
        assertTrue(only(5), "notification wave must redraw");
        assertTrue(only(6), "recorded-keystroke highlight must redraw");
    }

    /**
     * Invokes {@link CamelMonitor#needsAnimationRedraw} with exactly one of the seven flags set, proving that source
     * triggers a redraw on its own.
     */
    private static boolean only(int index) {
        boolean[] flags = new boolean[7];
        flags[index] = true;
        return CamelMonitor.needsAnimationRedraw(
                flags[0], flags[1], flags[2], flags[3], flags[4], flags[5], flags[6]);
    }
}
