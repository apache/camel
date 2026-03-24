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
package org.apache.camel.dsl.jbang.core.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalWidthHelperTest {

    // --- getTerminalWidth ---

    @Test
    void getTerminalWidthReturnsPositiveValue() {
        int width = TerminalWidthHelper.getTerminalWidth();
        assertTrue(width >= 40, "Terminal width should be at least 40, got: " + width);
    }

    // --- flexWidth ---

    @Test
    void flexWidthReturnsMaxOnWideTerminal() {
        // 200 cols - 50 fixed - 10 borders = 140 available; capped at max 80
        assertEquals(80, TerminalWidthHelper.flexWidth(200, 50, 10, 20, 80));
    }

    @Test
    void flexWidthReturnsAvailableOnMediumTerminal() {
        // 120 cols - 50 fixed - 10 borders = 60 available; between min 20 and max 80
        assertEquals(60, TerminalWidthHelper.flexWidth(120, 50, 10, 20, 80));
    }

    @Test
    void flexWidthReturnsMinOnNarrowTerminal() {
        // 60 cols - 50 fixed - 10 borders = 0 available; floored at min 20
        assertEquals(20, TerminalWidthHelper.flexWidth(60, 50, 10, 20, 80));
    }

    @Test
    void flexWidthReturnsMinWhenAvailableIsNegative() {
        // 40 cols - 80 fixed - 10 borders = -50 available; floored at min 15
        assertEquals(15, TerminalWidthHelper.flexWidth(40, 80, 10, 15, 80));
    }

    @Test
    void flexWidthExactFit() {
        // 100 cols - 50 fixed - 10 borders = 40 available; exactly at max 40
        assertEquals(40, TerminalWidthHelper.flexWidth(100, 50, 10, 20, 40));
    }

    // --- scaleWidth ---

    @Test
    void scaleWidthReturnsPreferredOnWideTerminal() {
        // 200 cols - 13 borders = 187 available >= 165 total preferred → return preferred
        assertEquals(80, TerminalWidthHelper.scaleWidth(200, 13, 80, 20, 165));
        assertEquals(35, TerminalWidthHelper.scaleWidth(200, 13, 35, 12, 165));
        assertEquals(25, TerminalWidthHelper.scaleWidth(200, 13, 25, 8, 165));
    }

    @Test
    void scaleWidthScalesProportionallyOnNarrowTerminal() {
        // 85 cols - 13 borders = 72 available < 165 total preferred
        // 80 column should scale to 72*80/165 = 34
        assertEquals(34, TerminalWidthHelper.scaleWidth(85, 13, 80, 15, 165));
        // 35 column should scale to 72*35/165 = 15
        assertEquals(15, TerminalWidthHelper.scaleWidth(85, 13, 35, 12, 165));
        // 25 column should scale to 72*25/165 = 10
        assertEquals(10, TerminalWidthHelper.scaleWidth(85, 13, 25, 8, 165));
    }

    @Test
    void scaleWidthRespectsMinWidth() {
        // 50 cols - 13 borders = 37 available < 165 total preferred
        // 25 column would scale to 37*25/165 = 5, but min is 8
        assertEquals(8, TerminalWidthHelper.scaleWidth(50, 13, 25, 8, 165));
    }

    @Test
    void scaleWidthNeverExceedsPreferred() {
        // Even if available > allPreferred, don't exceed preferred
        assertEquals(35, TerminalWidthHelper.scaleWidth(500, 13, 35, 12, 165));
    }

    // --- noBorderOverhead ---

    @Test
    void noBorderOverheadSingleColumn() {
        assertEquals(0, TerminalWidthHelper.noBorderOverhead(1));
    }

    @Test
    void noBorderOverheadMultipleColumns() {
        assertEquals(2, TerminalWidthHelper.noBorderOverhead(2));
        assertEquals(6, TerminalWidthHelper.noBorderOverhead(4));
        assertEquals(16, TerminalWidthHelper.noBorderOverhead(9));
    }

    // --- fancyBorderOverhead ---

    @Test
    void fancyBorderOverheadSingleColumn() {
        // | col | = 2 borders + 2 padding + 0 separators = 4
        assertEquals(4, TerminalWidthHelper.fancyBorderOverhead(1));
    }

    @Test
    void fancyBorderOverheadFourColumns() {
        // | c1 | c2 | c3 | c4 | = 2 borders + 8 padding + 3 separators = 13
        assertEquals(13, TerminalWidthHelper.fancyBorderOverhead(4));
    }

    @Test
    void fancyBorderOverheadFiveColumns() {
        // | c1 | c2 | c3 | c4 | c5 | = 2 + 10 + 4 = 16
        assertEquals(16, TerminalWidthHelper.fancyBorderOverhead(5));
    }

    // --- Integration: scaleWidth produces widths that fit terminal ---

    @Test
    void scaledColumnsWithBordersFitTerminal() {
        // Simulate CatalogDoc 4-column table: NAME=35, DESC=80, DEFAULT=25, TYPE=25
        int terminalWidth = 85;
        int borders = TerminalWidthHelper.fancyBorderOverhead(4); // 13
        int totalPreferred = 35 + 80 + 25 + 25; // 165

        int nameW = TerminalWidthHelper.scaleWidth(terminalWidth, borders, 35, 12, totalPreferred);
        int descW = TerminalWidthHelper.scaleWidth(terminalWidth, borders, 80, 15, totalPreferred);
        int defW = TerminalWidthHelper.scaleWidth(terminalWidth, borders, 25, 8, totalPreferred);
        int typeW = TerminalWidthHelper.scaleWidth(terminalWidth, borders, 25, 8, totalPreferred);

        int total = nameW + descW + defW + typeW + borders;
        assertTrue(total <= terminalWidth,
                "Total table width %d should fit terminal %d (cols: %d+%d+%d+%d, borders: %d)"
                        .formatted(total, terminalWidth, nameW, descW, defW, typeW, borders));
    }

    @Test
    void scaledColumnsPreserveWidthOnWideTerminal() {
        // On a wide terminal, all columns should retain their preferred widths
        int terminalWidth = 200;
        int borders = TerminalWidthHelper.fancyBorderOverhead(4);
        int totalPreferred = 35 + 80 + 25 + 25;

        assertEquals(35, TerminalWidthHelper.scaleWidth(terminalWidth, borders, 35, 12, totalPreferred));
        assertEquals(80, TerminalWidthHelper.scaleWidth(terminalWidth, borders, 80, 15, totalPreferred));
        assertEquals(25, TerminalWidthHelper.scaleWidth(terminalWidth, borders, 25, 8, totalPreferred));
        assertEquals(25, TerminalWidthHelper.scaleWidth(terminalWidth, borders, 25, 8, totalPreferred));
    }

    @Test
    void flexWidthForNoBordersProcessCommand() {
        // Simulate ListProcess: 9 columns, fixed ~56 chars, NAME flex (max 40), error flex (max 70)
        int tw = 80;
        int borders = TerminalWidthHelper.noBorderOverhead(9); // 16
        int nameW = TerminalWidthHelper.flexWidth(tw, 56, borders, 15, 40);
        // 80 - 56 - 16 = 8, but min is 15
        assertEquals(15, nameW);

        tw = 120;
        nameW = TerminalWidthHelper.flexWidth(tw, 56, borders, 15, 40);
        // 120 - 56 - 16 = 48, capped at max 40
        assertEquals(40, nameW);

        tw = 100;
        nameW = TerminalWidthHelper.flexWidth(tw, 56, borders, 15, 40);
        // 100 - 56 - 16 = 28
        assertEquals(28, nameW);
    }
}
