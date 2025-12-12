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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PdfMergeTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Test
    public void testMerge() throws Exception {
        final String pdf1 = "PDF 1 text";
        PDDocument document1 = PDFUtil.textToPDF(pdf1);
        File pdfFile1 = File.createTempFile("pdf1", "pdf");
        document1.save(pdfFile1);
        final String pdf2 = "PDF 2 text";
        PDDocument document2 = PDFUtil.textToPDF(pdf2);
        File pdfFile2 = File.createTempFile("pdf2", "pdf");
        document2.save(pdfFile2);

        template.sendBodyAndHeader("direct:start", "", PdfHeaderConstants.FILES_TO_MERGE_HEADER_NAME,
                List.of(pdfFile1, pdfFile2));

        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(exchange -> {
            Object body = exchange.getIn().getBody();
            assertThat(body, instanceOf(ByteArrayOutputStream.class));
            try {
                PDDocument doc = Loader.loadPDF(
                        new RandomAccessReadBuffer(new ByteArrayInputStream(((ByteArrayOutputStream) body).toByteArray())));
                PDFTextStripper pdfTextStripper = new PDFTextStripper();
                String text = pdfTextStripper.getText(doc);
                assertEquals(2, doc.getNumberOfPages());
                assertThat(text, containsString("PDF 1 text"));
                assertThat(text, containsString("PDF 2 text"));
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
                        .to("pdf:merge")
                        .to("mock:result");
            }
        };
    }
}
