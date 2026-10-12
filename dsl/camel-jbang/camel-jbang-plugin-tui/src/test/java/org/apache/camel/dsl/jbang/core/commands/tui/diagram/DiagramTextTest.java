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
package org.apache.camel.dsl.jbang.core.commands.tui.diagram;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.text.CharWidth;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagramTextTest {

    @Test
    void wrapMeasuresByDisplayWidth() {
        // ten CJK characters are 20 columns
        assertThat(DiagramText.wrap("注文を処理する経路です", 10, 3))
                .allSatisfy(line -> assertThat(CharWidth.of(line)).isLessThanOrEqualTo(10))
                .hasSize(3);
        assertThat(DiagramText.wrap("timer:tick", 20, 3)).containsExactly("timer:tick");
        assertThat(DiagramText.wrap("send to kafka:orders", 12, 3)).containsExactly("send to", "kafka:orders");
    }

    @Test
    void wrapCutsTheLastLineWithASpaceBetweenTheParts() {
        assertThat(DiagramText.wrap("Logs incoming request bodies for debugging and monitoring purposes only", 20, 3))
                .hasSize(3)
                .last().satisfies(line -> assertThat(line).endsWith("...").doesNotContain("andmon"));
    }

    @Test
    void wrapCutsTheLastLineAsTheTextGoesOn() {
        // broken after the :, the cut last line has no space added where the text has none
        assertThat(DiagramText.wrap("abc:defghijklmnopqrstuvwxyz0123456789", 20, 1))
                .containsExactly("abc:defghijklmnop...");
    }

    @Test
    void writeClipsToTheAreaByColumns() {
        Rect area = new Rect(2, 0, 6, 1);
        Buffer buffer = Buffer.empty(new Rect(0, 0, 10, 1));

        // scrolled two columns to the left: the first CJK character is out of view
        DiagramText.write(buffer, area, 0, 0, "日本語です", Style.EMPTY);

        assertThat(buffer.get(2, 0).symbol()).isEqualTo("本");
        assertThat(buffer.get(4, 0).symbol()).isEqualTo("語");
        assertThat(buffer.get(6, 0).symbol()).isEqualTo("で");
        // nothing past the right edge of the area
        assertThat(buffer.get(8, 0).symbol()).isEqualTo(" ");
    }

    @Test
    void writeLeavesTheColumnOfAWideCharacterCutByTheEdgeBlank() {
        Rect area = new Rect(2, 0, 6, 1);
        Buffer buffer = Buffer.empty(new Rect(0, 0, 10, 1));

        // scrolled one column: the first CJK character takes the column left of the area and the first one in it
        DiagramText.write(buffer, area, 1, 0, "日本語です", Style.EMPTY);

        assertThat(buffer.get(2, 0).symbol()).isEqualTo(" ");
        assertThat(buffer.get(3, 0).symbol()).isEqualTo("本");
        assertThat(buffer.get(5, 0).symbol()).isEqualTo("語");
        // で would end past the right edge of the area
        assertThat(buffer.get(7, 0).symbol()).isEqualTo(" ");
    }

    @Test
    void fitCutsWithAnEllipsis() {
        assertThat(DiagramText.fit("orders", 10)).isEqualTo("orders");
        assertThat(DiagramText.fit("日本語の名前", 7)).isEqualTo("日本語…");
    }
}
