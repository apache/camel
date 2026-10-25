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

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;

/**
 * Where the key hints of the footer are, so a click on one presses its key. A hint is drawn by {@link TuiHelper#hint}
 * as a key span styled with {@link Theme#hintKey()} followed by its label; a click on either presses the key. A hint of
 * two keys ({@code +/-}, {@code f/F}, {@code c/Esc}) presses the key on the side of the slash that was clicked, and so
 * does a label naming both ({@code fold/expand all}); a label of one name ({@code zoom}, {@code close}) presses the
 * first key.
 */
final class HintClicks {

    private int row = -1;
    private int[] startX = new int[0];
    private int[] endX = new int[0];
    private KeyEvent[] keys = new KeyEvent[0];

    /** Records the hints of the spans drawn in the one-row {@code area}. */
    void capture(Rect area, List<Span> spans) {
        List<int[]> bounds = new ArrayList<>();
        List<KeyEvent> found = new ArrayList<>();
        Style hintKeyStyle = Theme.hintKey();
        int x = area.x();
        for (int i = 0; i < spans.size(); i++) {
            Span s = spans.get(i);
            if (hintKeyStyle.equals(s.style())) {
                Span label = i + 1 < spans.size() && !hintKeyStyle.equals(spans.get(i + 1).style()) ? spans.get(i + 1) : null;
                addHint(bounds, found, x, s, label);
            }
            x += s.width();
        }
        row = area.y();
        startX = bounds.stream().mapToInt(b -> b[0]).toArray();
        endX = bounds.stream().mapToInt(b -> b[1]).toArray();
        keys = found.toArray(new KeyEvent[0]);
    }

    private static void addHint(List<int[]> bounds, List<KeyEvent> found, int x, Span key, Span label) {
        int keyEnd = x + key.width();
        int labelEnd = keyEnd + (label != null ? label.width() : 0);
        KeyEvent single = keyEvent(key.content());
        if (single != null) {
            bounds.add(new int[] { x, labelEnd });
            found.add(single);
            return;
        }
        KeyEvent[] pair = keyPair(key.content());
        if (pair == null) {
            return;
        }
        // the slash itself presses neither key
        int slash = x + CharWidth.of(key.content().substring(0, key.content().indexOf('/')));
        bounds.add(new int[] { x, slash });
        found.add(pair[0]);
        bounds.add(new int[] { slash + 1, keyEnd });
        found.add(pair[1]);
        if (label == null) {
            return;
        }
        int labelSlash = label.content().indexOf('/');
        if (labelSlash < 0) {
            bounds.add(new int[] { keyEnd, labelEnd });
            found.add(pair[0]);
        } else {
            int split = keyEnd + CharWidth.of(label.content().substring(0, labelSlash));
            bounds.add(new int[] { keyEnd, split });
            found.add(pair[0]);
            bounds.add(new int[] { split + 1, labelEnd });
            found.add(pair[1]);
        }
    }

    /** Forgets the hints: the footer drawn now has none to click (a flash message, the help). */
    void clear() {
        row = -1;
    }

    /** The key of the hint under a click, or null. */
    KeyEvent keyAt(int x, int y) {
        if (row < 0 || y != row) {
            return null;
        }
        int idx = regionAt(startX, endX, x);
        return idx >= 0 ? keys[idx] : null;
    }

    /**
     * Returns the index of the region whose column range contains {@code mouseX}, or {@code -1} when the click is
     * outside every region. Each region spans the half-open range {@code [startX[i], endX[i])}.
     */
    static int regionAt(int[] startX, int[] endX, int mouseX) {
        if (startX == null) {
            return -1;
        }
        for (int i = 0; i < startX.length; i++) {
            if (mouseX >= startX[i] && mouseX < endX[i]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Maps a hint key token (the trimmed content of a {@link Theme#hintKey()} span) to the {@link KeyEvent} that
     * pressing it would produce, or {@code null} when the token is not one key. Recognizes the function keys
     * {@code F1}-{@code F12}, {@code Enter}, {@code Esc}, {@code Tab}, {@code Space} and any single character; hints
     * such as {@code Up/Down} or arrow glyphs are not one key.
     */
    static KeyEvent keyEvent(String token) {
        if (token == null) {
            return null;
        }
        String t = token.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() >= 2 && (t.charAt(0) == 'F' || t.charAt(0) == 'f')
                && t.chars().skip(1).allMatch(Character::isDigit)) {
            KeyCode fkey = functionKeyCode(Integer.parseInt(t.substring(1)));
            return fkey != null ? KeyEvent.ofKey(fkey) : null;
        }
        return switch (t) {
            case "Enter" -> KeyEvent.ofKey(KeyCode.ENTER);
            case "Esc" -> KeyEvent.ofKey(KeyCode.ESCAPE);
            case "Tab" -> KeyEvent.ofKey(KeyCode.TAB);
            case "Space" -> KeyEvent.ofChar(' ', KeyModifiers.NONE);
            default -> t.length() == 1 ? KeyEvent.ofChar(t.charAt(0), KeyModifiers.NONE) : null;
        };
    }

    /**
     * The two keys of a hint such as {@code +/-}, {@code f/F} or {@code Esc/c}, or null when the token is not two keys
     * around one slash.
     */
    static KeyEvent[] keyPair(String token) {
        if (token == null) {
            return null;
        }
        String t = token.trim();
        int slash = t.indexOf('/');
        if (slash <= 0 || slash != t.lastIndexOf('/')) {
            return null;
        }
        KeyEvent first = keyEvent(t.substring(0, slash));
        KeyEvent second = keyEvent(t.substring(slash + 1));
        return first != null && second != null ? new KeyEvent[] { first, second } : null;
    }

    private static KeyCode functionKeyCode(int n) {
        return switch (n) {
            case 1 -> KeyCode.F1;
            case 2 -> KeyCode.F2;
            case 3 -> KeyCode.F3;
            case 4 -> KeyCode.F4;
            case 5 -> KeyCode.F5;
            case 6 -> KeyCode.F6;
            case 7 -> KeyCode.F7;
            case 8 -> KeyCode.F8;
            case 9 -> KeyCode.F9;
            case 10 -> KeyCode.F10;
            case 11 -> KeyCode.F11;
            case 12 -> KeyCode.F12;
            default -> null;
        };
    }
}
