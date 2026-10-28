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
import java.util.List;

import org.apache.camel.component.pdf.PdfConfiguration;

/**
 * Builds set of classes for line-termination writing strategy. Text getting sliced by line termination symbol (\r\n, \r
 * or \n) and then it will be written regardless it fits in the line or not. A tab is written as spaces, and other
 * control characters, which the fonts cannot show, are left out.
 */
public class LineTerminationWriterAbstractFactory implements TextProcessingAbstractFactory {

    private static final String LINE_END = "\\r\\n|\\r|\\n";
    private static final String TAB_SPACES = "    ";

    private final PdfConfiguration pdfConfiguration;

    public LineTerminationWriterAbstractFactory(PdfConfiguration pdfConfiguration) {
        this.pdfConfiguration = pdfConfiguration;
    }

    @Override
    public WriteStrategy createWriteStrategy() {
        return new DefaultWriteStrategy(pdfConfiguration);
    }

    @Override
    public SplitStrategy createSplitStrategy() {
        return new PatternSplitStrategy(LINE_END);
    }

    @Override
    public LineBuilderStrategy createLineBuilderStrategy() {
        // Do not format when LineByLineWriter is used, only leave out what the font cannot show.
        return new LineBuilderStrategy() {
            @Override
            public Collection<String> buildLines(Collection<String> splittedText) throws IOException {
                List<String> lines = new ArrayList<>(splittedText.size());
                for (String line : splittedText) {
                    lines.add(toPrintable(line));
                }
                return lines;
            }
        };
    }

    static String toPrintable(String line) {
        StringBuilder sb = null;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (Character.isISOControl(ch)) {
                if (sb == null) {
                    sb = new StringBuilder(line.length() + TAB_SPACES.length());
                    sb.append(line, 0, i);
                }
                if (ch == '\t') {
                    sb.append(TAB_SPACES);
                }
            } else if (sb != null) {
                sb.append(ch);
            }
        }
        return sb != null ? sb.toString() : line;
    }
}
