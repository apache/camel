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

import dev.tamboui.style.Color;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DrawOverlayTest {

    @Test
    void parseColorTakesTheNamesTheToolsDocument() {
        assertThat(DrawOverlay.parseColor("red")).isEqualTo(Color.RED);
        assertThat(DrawOverlay.parseColor(" Yellow ")).isEqualTo(Color.YELLOW);
        assertThat(DrawOverlay.parseColor("grey")).isEqualTo(Color.GRAY);
        assertThat(DrawOverlay.parseColor("black")).isEqualTo(Color.BLACK);
    }

    @Test
    void parseColorTakesLightAndDarkWithOrWithoutSeparator() {
        assertThat(DrawOverlay.parseColor("light_red")).isEqualTo(Color.LIGHT_RED);
        assertThat(DrawOverlay.parseColor("lightblue")).isEqualTo(Color.LIGHT_BLUE);
        assertThat(DrawOverlay.parseColor("light-cyan")).isEqualTo(Color.LIGHT_CYAN);
        assertThat(DrawOverlay.parseColor("dark_gray")).isEqualTo(Color.DARK_GRAY);
        assertThat(DrawOverlay.parseColor("darkgrey")).isEqualTo(Color.DARK_GRAY);
        assertThat(DrawOverlay.parseColor("bright-green")).isEqualTo(Color.LIGHT_GREEN);
    }

    @Test
    void parseColorTakesHexRgbAndIndexed() {
        assertThat(DrawOverlay.parseColor("#ff8000")).isEqualTo(Color.rgb(255, 128, 0));
        assertThat(DrawOverlay.parseColor("#F80")).isEqualTo(Color.rgb(255, 136, 0));
        assertThat(DrawOverlay.parseColor("rgb(1, 2, 3)")).isEqualTo(Color.rgb(1, 2, 3));
        assertThat(DrawOverlay.parseColor("indexed(208)")).isEqualTo(Color.indexed(208));
    }

    @Test
    void parseColorAnswersNullForWhatIsNotAColor() {
        assertThat(DrawOverlay.parseColor(null)).isNull();
        assertThat(DrawOverlay.parseColor(" ")).isNull();
        assertThat(DrawOverlay.parseColor("chartreuse-ish")).isNull();
        assertThat(DrawOverlay.parseColor("#12")).isNull();
    }
}
