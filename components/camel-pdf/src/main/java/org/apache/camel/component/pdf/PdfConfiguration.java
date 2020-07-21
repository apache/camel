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

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

import static org.apache.camel.component.pdf.PdfPageSizeConstant.PAGE_SIZE_A0;
import static org.apache.camel.component.pdf.PdfPageSizeConstant.PAGE_SIZE_A1;
import static org.apache.camel.component.pdf.PdfPageSizeConstant.PAGE_SIZE_A2;
import static org.apache.camel.component.pdf.PdfPageSizeConstant.PAGE_SIZE_A3;
import static org.apache.camel.component.pdf.PdfPageSizeConstant.PAGE_SIZE_A4;
import static org.apache.camel.component.pdf.PdfPageSizeConstant.PAGE_SIZE_A5;
import static org.apache.camel.component.pdf.PdfPageSizeConstant.PAGE_SIZE_A6;
import static org.apache.camel.component.pdf.PdfPageSizeConstant.PAGE_SIZE_LETTER;

/**
 * Handles pdf component configuration values.
 */
@UriParams
public class PdfConfiguration {
    private static final Map<String, PDRectangle> PAGE_MAP = new HashMap<>();

    static {
        PAGE_MAP.put(PAGE_SIZE_A0, PDRectangle.A0);
        PAGE_MAP.put(PAGE_SIZE_A1, PDRectangle.A1);
        PAGE_MAP.put(PAGE_SIZE_A2, PDRectangle.A2);
        PAGE_MAP.put(PAGE_SIZE_A3, PDRectangle.A3);
        PAGE_MAP.put(PAGE_SIZE_A4, PDRectangle.A4);
        PAGE_MAP.put(PAGE_SIZE_A5, PDRectangle.A5);
        PAGE_MAP.put(PAGE_SIZE_A6, PDRectangle.A6);
        PAGE_MAP.put(PAGE_SIZE_LETTER, PDRectangle.LETTER);
    }

    @UriPath(description = "Operation type")
    @Metadata(required = true)
    private PdfOperation operation;
    @UriParam(defaultValue = "20")
    private int marginTop = 20;
    @UriParam(defaultValue = "20")
    private int marginBottom = 20;
    @UriParam(defaultValue = "20")
    private int marginLeft = 20;
    @UriParam(defaultValue = "40")
    private int marginRight = 40;
    @UriParam(defaultValue = "14")
    private float fontSize = 14;
    @UriParam(defaultValue = "A4", enums = "LETTER,LEGAL,A0,A1,A2,A3,A4,A5,A6")
    private String pageSize = PAGE_SIZE_A4;
    @UriParam(defaultValue = "Helvetica", enums = "Courier,Courier-Bold,Courier-Oblique,Courier-BoldOblique,"
            + "Helvetica,Helvetica-Bold,Helvetica-Oblique,Helvetica-BoldOblique,"
            + "Times-Roman,Times-Bold,Times-Italic,Times-BoldItalic,"
            + "Symbol,ZapfDingbats")
    private String font = "Helvetica";
    @UriParam(defaultValue = "lineTermination")
    private TextProcessingFactory textProcessingFactory = TextProcessingFactory.lineTermination;

    public PdfOperation getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = PdfOperation.valueOf(operation);
    }

    public void setOperation(PdfOperation operation) {
        this.operation = operation;
    }

    public int getMarginTop() {
        return marginTop;
    }

    /**
     * Margin top in pixels
     */
    public void setMarginTop(int marginTop) {
        this.marginTop = marginTop;
    }

    public int getMarginBottom() {
        return marginBottom;
    }

    /**
     * Margin bottom in pixels
     */
    public void setMarginBottom(int marginBottom) {
        this.marginBottom = marginBottom;
    }

    public int getMarginLeft() {
        return marginLeft;
    }

    /**
     * Margin left in pixels
     */
    public void setMarginLeft(int marginLeft) {
        this.marginLeft = marginLeft;
    }

    public int getMarginRight() {
        return marginRight;
    }

    /**
     * Margin right in pixels
     */
    public void setMarginRight(int marginRight) {
        this.marginRight = marginRight;
    }

    public float getFontSize() {
        return fontSize;
    }

    /**
     * Font size in pixels
     */
    public void setFontSize(float fontSize) {
        this.fontSize = fontSize;
    }

    public PDRectangle getPageSize() {
        return PAGE_MAP.get(pageSize);
    }

    /**
     * Page size
     */
    public void setPageSize(String pageSize) {
        this.pageSize = pageSize;
    }

    public PDFont getFont() {
        return Standard14Fonts.getByName(font);
    }

    /**
     * Font
     */
    public void setFont(String font) {
        this.font = font;
    }

    public TextProcessingFactory getTextProcessingFactory() {
        return textProcessingFactory;
    }

    /**
     * Text processing to use.
     * <ul>
     *   <li>autoFormatting: Text is getting sliced by words, then max amount of words that fits in the line will
     *   be written into pdf document. With this strategy all words that doesn't fit in the line will be moved to the new line.</li>
     *   <li>lineTermination: Builds set of classes for line-termination writing strategy. Text getting sliced by line termination symbol
     *   and then it will be written regardless it fits in the line or not.</li>
     * </ul>
     */
    public void setTextProcessingFactory(TextProcessingFactory textProcessingFactory) {
        this.textProcessingFactory = textProcessingFactory;
    }

    public void setTextProcessingFactory(String textProcessingFactory) {
        this.textProcessingFactory = TextProcessingFactory.valueOf(textProcessingFactory);
    }
}
