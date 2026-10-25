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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardDecryptionMaterial;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PdfTextExtractionTest extends CamelTestSupport {

    private static final String EXPECTED_TEXT = "Test string";

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @TempDir
    File tempDir;

    @Test
    public void testExtractText() throws Exception {
        PDDocument document = PDFUtil.textToPDF(EXPECTED_TEXT);

        expectText();
        template.sendBody("direct:start", document);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testExtractTextFromEncrypted() throws Exception {
        final String ownerPass = "ownerPass";
        final String userPass = "userPass";
        AccessPermission accessPermission = new AccessPermission();
        accessPermission.setCanExtractContent(false);
        StandardProtectionPolicy protectionPolicy = new StandardProtectionPolicy(ownerPass, userPass, accessPermission);
        protectionPolicy.setEncryptionKeyLength(128);

        PDDocument document = PDFUtil.textToPDF(EXPECTED_TEXT);

        document.protect(protectionPolicy);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output);

        // Encryption happens after saving.
        PDDocument encryptedDocument = Loader.loadPDF(output.toByteArray(), userPass);

        expectText();
        template.sendBodyAndHeader("direct:start",
                encryptedDocument,
                PdfHeaderConstants.DECRYPTION_MATERIAL_HEADER_NAME,
                new StandardDecryptionMaterial(userPass));
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testExtractTextFromBytes() throws Exception {
        expectText();
        template.sendBody("direct:start", pdfBytes());
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testExtractTextFromByteArrayOutputStream() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(pdfBytes());

        expectText();
        template.sendBody("direct:start", bos);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testExtractTextFromInputStream() throws Exception {
        expectText();
        template.sendBody("direct:start", new ByteArrayInputStream(pdfBytes()));
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testExtractTextFromFile() throws Exception {
        File file = new File(tempDir, "test.pdf");
        try (PDDocument document = PDFUtil.textToPDF(EXPECTED_TEXT)) {
            document.save(file);
        }

        expectText();
        template.sendBody("direct:start", file);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testExtractTextFromText() {
        Exception e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", "not a pdf"));
        assertInstanceOf(InvalidPayloadException.class, e.getCause());
        assertThat(e.getCause().getMessage(), containsString("java.lang.String"));
    }

    @Test
    public void testExtractTextWithoutBody() {
        Exception e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", null));
        assertInstanceOf(InvalidPayloadException.class, e.getCause());
    }

    private void expectText() {
        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(exchange -> {
            Object body = exchange.getIn().getBody();
            assertThat(body, instanceOf(String.class));
            assertThat((String) body, containsString(EXPECTED_TEXT));
            return true;
        });
    }

    private static byte[] pdfBytes() throws Exception {
        try (PDDocument document = PDFUtil.textToPDF(EXPECTED_TEXT)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            document.save(bos);
            return bos.toByteArray();
        }
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
