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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * Create, modify or extract content from PDF documents.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "pdf", title = "PDF", syntax = "pdf:operation", producerOnly = true,
             remote = false, category = { Category.DOCUMENT, Category.TRANSFORMATION },
             headersClass = PdfHeaderConstants.class)
@BodyTypes(producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "extractText",
                                                         carriers = {
                                                                 Carrier.BYTES, Carrier.STREAM, Carrier.FILE,
                                                                 Carrier.POJO }),
                                          @BodyType.When(option = "operation", value = "merge", carriers = Carrier.ANY) },
                                  description = "The text to write for create and append, which takes the document from the"
                                                + " CamelPdfDocument header; a Map or a POJO is written as its toString:"
                                                + " marshal it first. extractText reads a PDF from bytes, such as the"
                                                + " ByteArrayOutputStream of create, a stream, a file or a PDDocument, not from"
                                                + " text; another body, or none, fails. merge takes the files from the"
                                                + " CamelPdfFilesToMerge header"),
           producerOut = @BodyType(value = Carrier.BYTES, javaType = "java.io.ByteArrayOutputStream",
                                   byOption = @BodyType.When(option = "operation", value = "extractText",
                                                             carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                   description = "The PDF of create, append and merge as bytes, in a ByteArrayOutputStream,"
                                                 + " which extractText reads as it is. The text of the document for"
                                                 + " extractText"))
public class PdfEndpoint extends DefaultEndpoint {

    @UriParam
    private PdfConfiguration pdfConfiguration;

    public PdfEndpoint(String endpointUri, Component component, PdfConfiguration pdfConfiguration) {
        super(endpointUri, component);
        this.pdfConfiguration = pdfConfiguration;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        String fontFile = pdfConfiguration.getFontFile();
        if (fontFile != null && pdfConfiguration.getFontFileContent() == null) {
            // load the font once, and fail when the endpoint is created rather than with the first message
            byte[] content;
            InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), fontFile);
            try {
                content = is.readAllBytes();
            } finally {
                IOHelper.close(is);
            }
            // check that PDFBox can use the font, such as not an OpenType font with CFF outlines
            try (PDDocument document = new PDDocument()) {
                PDType0Font.load(document, new ByteArrayInputStream(content));
            } catch (IOException e) {
                throw new IOException(String.format("Cannot use the fontFile %s: %s", fontFile, e.getMessage()), e);
            }
            pdfConfiguration.setFontFileContent(content);
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        return new PdfProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer does not supported for PDF component:" + getEndpointUri());
    }

    public PdfConfiguration getPdfConfiguration() {
        return pdfConfiguration;
    }

    public void setPdfConfiguration(PdfConfiguration pdfConfiguration) {
        this.pdfConfiguration = pdfConfiguration;
    }
}
