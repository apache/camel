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
import java.util.Collection;

import org.apache.camel.component.pdf.PdfConfiguration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 * Writes given lines to PDF document. If document already contains some text then new text will be appended to new
 * page.
 */
public class DefaultWriteStrategy implements WriteStrategy {

    private final PdfConfiguration pdfConfiguration;

    public DefaultWriteStrategy(PdfConfiguration pdfConfiguration) {
        this.pdfConfiguration = pdfConfiguration;
    }

    @Override
    public PDDocument write(Collection<String> lines, PDDocument document) throws IOException {
        PDPage page = new PDPage(pdfConfiguration.getPageSize());
        document.addPage(page);
        float x = pdfConfiguration.getMarginLeft();
        float y = page.getMediaBox().getHeight() - pdfConfiguration.getMarginTop();
        // one font for all the pages, so a font file is embedded once in the document
        PDFont font = PdfUtils.createFont(pdfConfiguration, document);
        float averageFontHeight = PdfUtils.getAverageFontHeight(font, pdfConfiguration.getFontSize());
        float lineSpacing = averageFontHeight * 2;

        PDPageContentStream contentStream = initializeContentStream(document, page, font);
        for (String line : lines) {
            writeLine(x, y, line, contentStream);
            y -= lineSpacing;
            if (goToNextPage(y)) {
                contentStream.close();
                page = new PDPage(pdfConfiguration.getPageSize());
                document.addPage(page);
                contentStream = initializeContentStream(document, page, font);
                y = page.getMediaBox().getHeight() - pdfConfiguration.getMarginTop();
            }
        }
        contentStream.close();
        return document;
    }

    private boolean goToNextPage(float y) {
        return y < pdfConfiguration.getMarginBottom();
    }

    private void writeLine(float x, float y, String currentLine, PDPageContentStream contentStream) throws IOException {
        contentStream.beginText();
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(currentLine);
        contentStream.endText();
    }

    private PDPageContentStream initializeContentStream(PDDocument document, PDPage page, PDFont font) throws IOException {
        PDPageContentStream contentStream = new PDPageContentStream(document, page);
        contentStream.setFont(font, pdfConfiguration.getFontSize());
        return contentStream;
    }
}
