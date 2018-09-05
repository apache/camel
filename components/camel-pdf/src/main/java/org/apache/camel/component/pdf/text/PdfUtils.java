/**
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

import org.apache.pdfbox.pdmodel.font.PDFont;

import static org.apache.camel.component.pdf.PdfConstants.PDF_PIXEL_SIZE;

public final class PdfUtils {
    private PdfUtils() { }

    public static float getAverageFontHeight(PDFont font, float fontSize) throws IOException {
        return font.getBoundingBox().getHeight() / PDF_PIXEL_SIZE * fontSize;
    }

    public static float getFontHeightForString(String str, PDFont font, float fontSize) throws IOException {
        return font.getBoundingBox().getHeight() / PDF_PIXEL_SIZE * fontSize;
    }

    public static float getFontWidth(String str, PDFont font, float fontSize) throws IOException {
        return font.getStringWidth(str) / PDF_PIXEL_SIZE * fontSize;
    }
}
