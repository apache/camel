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
package org.apache.camel.component.shell;

import org.jline.utils.AttributedStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShellConsumerColorTest {

    @Test
    void testResolveColorNull() {
        assertEquals(AttributedStyle.CYAN, ShellConsumer.resolveColor(null));
    }

    @Test
    void testResolveColorUnknown() {
        assertEquals(AttributedStyle.CYAN, ShellConsumer.resolveColor("purple"));
    }

    @Test
    void testResolveColorBlack() {
        assertEquals(AttributedStyle.BLACK, ShellConsumer.resolveColor("black"));
    }

    @Test
    void testResolveColorRed() {
        assertEquals(AttributedStyle.RED, ShellConsumer.resolveColor("red"));
    }

    @Test
    void testResolveColorGreen() {
        assertEquals(AttributedStyle.GREEN, ShellConsumer.resolveColor("green"));
    }

    @Test
    void testResolveColorYellow() {
        assertEquals(AttributedStyle.YELLOW, ShellConsumer.resolveColor("yellow"));
    }

    @Test
    void testResolveColorBlue() {
        assertEquals(AttributedStyle.BLUE, ShellConsumer.resolveColor("blue"));
    }

    @Test
    void testResolveColorMagenta() {
        assertEquals(AttributedStyle.MAGENTA, ShellConsumer.resolveColor("magenta"));
    }

    @Test
    void testResolveColorCyan() {
        assertEquals(AttributedStyle.CYAN, ShellConsumer.resolveColor("cyan"));
    }

    @Test
    void testResolveColorWhite() {
        assertEquals(AttributedStyle.WHITE, ShellConsumer.resolveColor("white"));
    }

    @Test
    void testResolveColorCaseInsensitive() {
        assertEquals(AttributedStyle.GREEN, ShellConsumer.resolveColor("GREEN"));
        assertEquals(AttributedStyle.RED, ShellConsumer.resolveColor("Red"));
        assertEquals(AttributedStyle.BLUE, ShellConsumer.resolveColor("BLUE"));
    }
}
