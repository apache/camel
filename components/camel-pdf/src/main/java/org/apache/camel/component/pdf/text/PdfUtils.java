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

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.camel.component.pdf.PdfConfiguration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import static org.apache.camel.component.pdf.PdfConstants.PDF_PIXEL_SIZE;

public final class PdfUtils {
    private PdfUtils() {
    }

    /**
     * The font to write the text with: the font file of the fontFile option, which is embedded in the given document,
     * or else the standard font of the font option.
     */
    public static PDFont createFont(PdfConfiguration pdfConfiguration, PDDocument document) throws IOException {
        byte[] fontFile = pdfConfiguration.getFontFileContent();
        if (fontFile != null) {
            // a Type0 font belongs to the document it is embedded in, so it is loaded for each document
            return PDType0Font.load(document, new ByteArrayInputStream(fontFile));
        }
        return new PDType1Font(Standard14Fonts.FontName.valueOf(pdfConfiguration.getFont()));
    }

    public static float getAverageFontHeight(PDFont font, float fontSize) throws IOException {
        return font.getBoundingBox().getHeight() / PDF_PIXEL_SIZE * fontSize;
    }

    public static float getFontWidth(String str, PDFont font, float fontSize) throws IOException {
        return font.getStringWidth(str) / PDF_PIXEL_SIZE * fontSize;
    }
}
