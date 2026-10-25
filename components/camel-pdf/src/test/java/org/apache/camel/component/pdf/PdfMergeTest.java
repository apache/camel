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
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PdfMergeTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @TempDir
    File tempDir;

    private File pdfFile1;
    private File pdfFile2;

    @BeforeEach
    void createPdfFiles() throws IOException {
        pdfFile1 = new File(tempDir, "pdf1.pdf");
        try (PDDocument document1 = PDFUtil.textToPDF("PDF 1 text")) {
            document1.save(pdfFile1);
        }
        pdfFile2 = new File(tempDir, "pdf2.pdf");
        try (PDDocument document2 = PDFUtil.textToPDF("PDF 2 text")) {
            document2.save(pdfFile2);
        }
    }

    @Test
    public void testMerge() throws Exception {
        expectMerged();
        template.sendBodyAndHeader("direct:start", "", PdfHeaderConstants.FILES_TO_MERGE_HEADER_NAME,
                List.of(pdfFile1, pdfFile2));
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testMergePath() throws Exception {
        expectMerged();
        template.sendBodyAndHeader("direct:start", "", PdfHeaderConstants.FILES_TO_MERGE_HEADER_NAME,
                List.of(pdfFile1.toPath(), pdfFile2.toPath()));
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testMergeUnknownElement() {
        Exception e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader("direct:start", "", PdfHeaderConstants.FILES_TO_MERGE_HEADER_NAME,
                        List.of(pdfFile1, pdfFile2.getAbsolutePath())));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertThat(e.getCause().getMessage(), containsString("java.lang.String"));
    }

    private void expectMerged() {
        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(exchange -> {
            Object body = exchange.getIn().getBody();
            assertThat(body, instanceOf(ByteArrayOutputStream.class));
            try (PDDocument doc = Loader.loadPDF(((ByteArrayOutputStream) body).toByteArray())) {
                String text = new PDFTextStripper().getText(doc);
                assertEquals(2, doc.getNumberOfPages());
                assertThat(text, containsString("PDF 1 text"));
                assertThat(text, containsString("PDF 2 text"));
            } catch (IOException e) {
                throw new RuntimeCamelException(e);
            }
            return true;
        });
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
