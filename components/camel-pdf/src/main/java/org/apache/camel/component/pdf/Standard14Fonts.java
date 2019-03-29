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
package org.apache.camel.component.pdf;

import java.util.HashMap;
import java.util.Map;

import org.apache.pdfbox.pdmodel.font.PDType1Font;

/**
 * The 14 standard fonts by name. Created here because there is no way to get a standard font by name in {@code pdfbox} 2.0
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public final class Standard14Fonts {
    private static final Map<String, PDType1Font> FONTS_BY_NAME = new HashMap<>();
    static {
        FONTS_BY_NAME.put(PDType1Font.TIMES_ROMAN.getBaseFont(), PDType1Font.TIMES_ROMAN);
        FONTS_BY_NAME.put(PDType1Font.TIMES_BOLD.getBaseFont(), PDType1Font.TIMES_BOLD);
        FONTS_BY_NAME.put(PDType1Font.TIMES_ITALIC.getBaseFont(), PDType1Font.TIMES_ITALIC);
        FONTS_BY_NAME.put(PDType1Font.TIMES_BOLD_ITALIC.getBaseFont(), PDType1Font.TIMES_BOLD_ITALIC);
        FONTS_BY_NAME.put(PDType1Font.HELVETICA.getBaseFont(), PDType1Font.HELVETICA);
        FONTS_BY_NAME.put(PDType1Font.HELVETICA_BOLD.getBaseFont(), PDType1Font.HELVETICA_BOLD);
        FONTS_BY_NAME.put(PDType1Font.HELVETICA_OBLIQUE.getBaseFont(), PDType1Font.HELVETICA_OBLIQUE);
        FONTS_BY_NAME.put(PDType1Font.HELVETICA_BOLD_OBLIQUE.getBaseFont(), PDType1Font.HELVETICA_BOLD_OBLIQUE);
        FONTS_BY_NAME.put(PDType1Font.COURIER.getBaseFont(), PDType1Font.COURIER);
        FONTS_BY_NAME.put(PDType1Font.COURIER_BOLD.getBaseFont(), PDType1Font.COURIER_BOLD);
        FONTS_BY_NAME.put(PDType1Font.COURIER_OBLIQUE.getBaseFont(), PDType1Font.COURIER_OBLIQUE);
        FONTS_BY_NAME.put(PDType1Font.COURIER_BOLD_OBLIQUE.getBaseFont(), PDType1Font.COURIER_BOLD_OBLIQUE);
        FONTS_BY_NAME.put(PDType1Font.SYMBOL.getBaseFont(), PDType1Font.SYMBOL);
        FONTS_BY_NAME.put(PDType1Font.ZAPF_DINGBATS.getBaseFont(), PDType1Font.ZAPF_DINGBATS);
    }

    private Standard14Fonts() {
    }

    /**
     * Get one of the 14 standard fonts by base font name.
     *
     * @param fontName the base font name, such as {@code "Helvetica"} or {@code "Helvetica-Bold"}
     * @return the {@link PDType1Font} or null, of the {@code fontName} is not mapped to any font
     */
    public static PDType1Font getByName(String fontName) {
        return FONTS_BY_NAME.get(fontName);
    }

}
