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
package org.apache.camel.component.pdf.text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import org.apache.camel.component.pdf.PdfConfiguration;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import static org.apache.camel.component.pdf.PdfConstants.MIN_CONTENT_WIDTH;

/**
 * Builds lines from words based on line width and PDF document page size. Built lines then will be written to pdf
 * document.
 */
public class DefaultLineBuilderStrategy implements LineBuilderStrategy {
    private final PdfConfiguration pdfConfiguration;

    public DefaultLineBuilderStrategy(PdfConfiguration pdfConfiguration) {
        this.pdfConfiguration = pdfConfiguration;
    }

    /**
     * Builds lines from words. Utilizes the same behaviour as office software:
     * <ul>
     * <li>If word doesn't fit in current line, and current lines contains other words, then it will be moved to new
     * line.</td>
     * <li>Word doesn't fit in the line and line does not contain other words, then word will be slitted, and split
     * index will be on max amount of characters that fits in the line</li>
     * </ul>
     */
    @Override
    public Collection<String> buildLines(Collection<String> splittedText) throws IOException {
        LinkedList<String> wordsList = new LinkedList<>(splittedText);
        Collection<String> lines = new ArrayList<>();
        LineBuilder currentLine = new LineBuilder();
        float allowedLineWidth = getAllowedLineWidth();
        while (!wordsList.isEmpty()) {
            String word = wordsList.removeFirst();
            if (isWordFitInCurrentLine(currentLine, word, allowedLineWidth)) {
                currentLine.appendWord(word);
                if (wordsList.isEmpty()) {
                    lines.add(currentLine.buildLine());
                }
            } else if (currentLine.getWordsCount() != 0) {
                lines.add(currentLine.buildLine());
                wordsList.addFirst(word);
            } else {
                int splitIndex = findSplitIndex(word, allowedLineWidth);
                currentLine.appendWord(word.substring(0, splitIndex));
                lines.add(currentLine.buildLine());
                wordsList.addFirst(word.substring(splitIndex));
            }
        }
        return lines;
    }

    private int findSplitIndex(String word, float allowedLineWidth) throws IOException {
        // Using binary search algorithm to find max amount of characters that fit int the line.

        int middle = word.length() >> 1;
        int end = word.length();

        int currentSplitIndex = 0;

        do {
            if (isLineFitInLineWidth(word.substring(0, middle), allowedLineWidth)) {
                currentSplitIndex = middle;
                middle += word.substring(middle, end).length() >> 1;
            } else {
                end = middle;
                middle = currentSplitIndex + (word.substring(currentSplitIndex, middle).length() >> 1);
            }
        } while (currentSplitIndex == -1 || !isSplitIndexFound(word, allowedLineWidth, currentSplitIndex));

        return currentSplitIndex;
    }

    private boolean isSplitIndexFound(String word, float allowedLineWidth, int currentSplitIndex) throws IOException {
        return isLineFitInLineWidth(word.substring(0, currentSplitIndex), allowedLineWidth)
                && !isLineFitInLineWidth(word.substring(0, currentSplitIndex + 1), allowedLineWidth);
    }

    private boolean isWordFitInCurrentLine(LineBuilder currentLine, String word, float allowedLineWidth) throws IOException {
        LineBuilder lineBuilder = currentLine.clone().appendWord(word);
        return isLineFitInLineWidth(lineBuilder.buildLine(), allowedLineWidth);
    }

    private boolean isLineFitInLineWidth(String currentLine, float allowedLineWidth) throws IOException {
        float fontWidth = PdfUtils.getFontWidth(
                currentLine,
                new PDType1Font(Standard14Fonts.FontName.valueOf(pdfConfiguration.getFont())),
                pdfConfiguration.getFontSize());

        return fontWidth <= allowedLineWidth;
    }

    public float getAllowedLineWidth() {
        float result = pdfConfiguration.getPageSize().getWidth()
                       - pdfConfiguration.getMarginLeft()
                       - pdfConfiguration.getMarginRight();
        if (result < MIN_CONTENT_WIDTH) {
            throw new IllegalStateException(
                    String.format("Allowed line width cannot be < %d, make sure"
                                  + " (marginLeft + marginRight) < pageSize",
                            MIN_CONTENT_WIDTH));
        }
        return result;
    }

    private static final class LineBuilder {
        private StringBuilder line = new StringBuilder();
        private int wordsCount;

        LineBuilder() {
        }

        LineBuilder(String line, int wordsCount) {
            this.line = new StringBuilder(line);
            this.wordsCount = wordsCount;
        }

        public LineBuilder appendWord(String word) {
            line.append(word).append(" ");
            wordsCount++;
            return this;
        }

        public String buildLine() {
            String savedLine = this.line.toString();
            reset();
            return savedLine;
        }

        public int getWordsCount() {
            return wordsCount;
        }

        private void reset() {
            line = new StringBuilder();
            wordsCount = 0;
        }

        @Override
        public LineBuilder clone() {
            return new LineBuilder(this.line.toString(), this.wordsCount);
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this);
        }
    }

}
