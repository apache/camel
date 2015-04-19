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
package org.apache.camel.component.pdf;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import static org.apache.camel.component.pdf.PdfPageSizeConstant.*;

/**
 * Handles pdf component configuration values.
 */
@UriParams
public class PdfConfiguration {
    private static final Map<String, PDRectangle> PAGE_MAP = new HashMap<>();

    static {
        PAGE_MAP.put(PAGE_SIZE_A0, PDPage.PAGE_SIZE_A0);
        PAGE_MAP.put(PAGE_SIZE_A1, PDPage.PAGE_SIZE_A1);
        PAGE_MAP.put(PAGE_SIZE_A2, PDPage.PAGE_SIZE_A2);
        PAGE_MAP.put(PAGE_SIZE_A3, PDPage.PAGE_SIZE_A3);
        PAGE_MAP.put(PAGE_SIZE_A4, PDPage.PAGE_SIZE_A4);
        PAGE_MAP.put(PAGE_SIZE_A5, PDPage.PAGE_SIZE_A5);
        PAGE_MAP.put(PAGE_SIZE_A6, PDPage.PAGE_SIZE_A6);
        PAGE_MAP.put(PAGE_SIZE_LETTER, PDPage.PAGE_SIZE_LETTER);
    }

    @UriPath(description = "Operation type") @Metadata(required = "true")
    private PdfOperation operation;
    @UriParam
    private int marginTop = 20;
    @UriParam
    private int marginBottom = 20;
    @UriParam
    private int marginLeft = 20;
    @UriParam
    private int marginRight = 40;
    @UriParam
    private float fontSize = 14;
    @UriParam
    private PDRectangle pageSize = PDPage.PAGE_SIZE_A4;
    @UriParam
    private PDFont font = PDType1Font.HELVETICA;
    @UriParam
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

    public void setMarginTop(int marginTop) {
        this.marginTop = marginTop;
    }

    public int getMarginBottom() {
        return marginBottom;
    }

    public void setMarginBottom(int marginBottom) {
        this.marginBottom = marginBottom;
    }

    public int getMarginLeft() {
        return marginLeft;
    }

    public void setMarginLeft(int marginLeft) {
        this.marginLeft = marginLeft;
    }

    public int getMarginRight() {
        return marginRight;
    }

    public void setMarginRight(int marginRight) {
        this.marginRight = marginRight;
    }

    public float getFontSize() {
        return fontSize;
    }

    public void setFontSize(float fontSize) {
        this.fontSize = fontSize;
    }

    public PDRectangle getPageSize() {
        return pageSize;
    }

    public void setPageSize(PDRectangle pageSize) {
        this.pageSize = pageSize;
    }

    public void setPageSize(String pageSize) {
        setPageSize(PAGE_MAP.get(pageSize));
    }

    public PDFont getFont() {
        return font;
    }

    public void setFont(PDFont font) {
        this.font = font;
    }

    public void setFont(String font) {
        setFont(PDType1Font.getStandardFont(font));
    }

    public TextProcessingFactory getTextProcessingFactory() {
        return textProcessingFactory;
    }

    public void setTextProcessingFactory(TextProcessingFactory textProcessingFactory) {
        this.textProcessingFactory = textProcessingFactory;
    }

    public void setTextProcessingFactory(String textProcessingFactory) {
        this.textProcessingFactory = TextProcessingFactory.valueOf(textProcessingFactory);
    }
}
