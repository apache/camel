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
package org.apache.camel.component.fop;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.Test;

public class FopEndpointTest extends CamelTestSupport {

    @Test
    public void generatePdfFromXslfoWithSpecificText() throws Exception {
        Endpoint endpoint = context().getEndpoint("fop:pdf");
        Producer producer = endpoint.createProducer();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(FopHelper.decorateTextWithXSLFO("Test Content"));

        producer.process(exchange);
        PDDocument document = getDocumentFrom(exchange);
        String content = FopHelper.extractTextFrom(document);
        assertEquals("Test Content", content);
    }

    @Test
    public void specifyCustomUserConfigurationFile() throws Exception {
        FopEndpoint customConfiguredEndpoint = context()
                .getEndpoint("fop:pdf?userConfigURL=file:src/test/data/conf/testcfg.xml",
                        FopEndpoint.class);
        float customSourceResolution = customConfiguredEndpoint.getFopFactory().getSourceResolution();
        assertEquals(96.0, customSourceResolution, 0.1);
    }

    @Test
    public void specifyCustomUserConfigurationFileClasspath() throws Exception {
        FopEndpoint customConfiguredEndpoint = context()
                .getEndpoint("fop:pdf?userConfigURL=myconf/testcfg.xml",
                        FopEndpoint.class);
        float customSourceResolution = customConfiguredEndpoint.getFopFactory().getSourceResolution();
        assertEquals(96.0, customSourceResolution, 0.1);
    }

    @Test
    public void setPDFRenderingMetadataPerDocument() throws Exception {
        Endpoint endpoint = context().getEndpoint("fop:pdf");
        Producer producer = endpoint.createProducer();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("CamelFop.Render.Creator", "Test User");
        exchange.getIn().setBody(FopHelper.decorateTextWithXSLFO("Test Content"));

        producer.process(exchange);
        PDDocument document = getDocumentFrom(exchange);
        String creator = FopHelper.getDocumentMetadataValue(document, COSName.CREATOR);
        assertEquals("Test User", creator);
    }

    @Test
    public void encryptPdfWithUserPassword() throws Exception {
        Endpoint endpoint = context().getEndpoint("fop:pdf");
        Producer producer = endpoint.createProducer();
        Exchange exchange = new DefaultExchange(context);
        final String password = "secret";
        exchange.getIn().setHeader("CamelFop.Encrypt.userPassword", password);
        exchange.getIn().setBody(FopHelper.decorateTextWithXSLFO("Test Content"));

        producer.process(exchange);
        try (InputStream inputStream = exchange.getOut().getBody(InputStream.class)) {
            PDDocument document = PDDocument.load(inputStream, password);
            assertTrue(document.isEncrypted());
        }
    }

    @Test
    public void overridePdfOutputFormatToPlainText() throws Exception {
        String defaultOutputFormat = "pdf";
        Endpoint endpoint = context().getEndpoint("fop:" + defaultOutputFormat);
        Producer producer = endpoint.createProducer();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(FopConstants.CAMEL_FOP_OUTPUT_FORMAT, "txt");
        exchange.getIn().setBody(FopHelper.decorateTextWithXSLFO("Test Content"));

        producer.process(exchange);
        String plainText = exchange.getOut().getBody(String.class).trim();
        assertEquals("Test Content", plainText);
    }

    private PDDocument getDocumentFrom(Exchange exchange) throws IOException {
        InputStream inputStream = exchange.getOut().getBody(InputStream.class);
        return PDDocument.load(inputStream);
    }
}
