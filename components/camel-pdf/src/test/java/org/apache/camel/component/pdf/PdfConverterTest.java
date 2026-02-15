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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.camel.EndpointInject;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.pdf.PDFUtil.textToPDF;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PdfConverterTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Test
    public void testAppend() throws Exception {
        final String originalText = "Test";
        final PDDocument document = textToPDF(originalText);

        byte[] array;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            document.save(baos);

            array = baos.toByteArray();
            template.sendBodyAndHeader("direct:start", array, PdfHeaderConstants.PDF_DOCUMENT_HEADER_NAME, document);
        }

        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(exchange -> {
            PDDocument doc = exchange.getIn().getBody(PDDocument.class);

            try {
                PDFTextStripper pdfTextStripper = new PDFTextStripper();
                String text = pdfTextStripper.getText(doc);
                assertEquals(2, doc.getNumberOfPages());
                assertThat(text, containsString(originalText));
            } catch (IOException e) {
                throw new RuntimeCamelException(e);
            }
            return true;
        });
        resultEndpoint.assertIsSatisfied();

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("pdf:extractText")
                        .to("mock:result");
            }
        };
    }
}
