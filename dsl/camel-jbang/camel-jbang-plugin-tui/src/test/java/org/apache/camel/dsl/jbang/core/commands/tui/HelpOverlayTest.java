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

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpOverlayTest {

    // The help overlay is opened with either F1 or '?'. Pressing the same keys again must close it,
    // so the binding is a true toggle. '?' was previously ignored by the overlay (CAMEL: TUI help).

    @Test
    void questionMarkClosesOverlay() {
        HelpOverlay overlay = new HelpOverlay();
        overlay.open("# Help");
        assertTrue(overlay.isVisible());

        boolean handled = overlay.handleKeyEvent(KeyEvent.ofChar('?'));

        assertTrue(handled, "the overlay must consume the key while visible");
        assertFalse(overlay.isVisible(), "'?' must close the help overlay it opened");
    }

    @Test
    void f1ClosesOverlay() {
        HelpOverlay overlay = new HelpOverlay();
        overlay.open("# Help");

        overlay.handleKeyEvent(KeyEvent.ofKey(KeyCode.F1));

        assertFalse(overlay.isVisible(), "F1 must still close the help overlay");
    }

    @Test
    void quitKeyClosesOverlay() {
        HelpOverlay overlay = new HelpOverlay();
        overlay.open("# Help");

        overlay.handleKeyEvent(KeyEvent.ofChar('q'));

        assertFalse(overlay.isVisible(), "'q' must still close the help overlay");
    }

    @Test
    void unrelatedKeyKeepsOverlayOpen() {
        HelpOverlay overlay = new HelpOverlay();
        overlay.open("# Help");

        overlay.handleKeyEvent(KeyEvent.ofChar('x'));

        assertTrue(overlay.isVisible(), "an unrelated key must not close the help overlay");
    }
}
