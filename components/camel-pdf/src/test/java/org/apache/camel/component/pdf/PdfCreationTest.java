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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PdfCreationTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Test
    public void testPdfCreation() throws Exception {
        final String expectedText = "expectedText";
        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(exchange -> {
            Object body = exchange.getIn().getBody();
            assertThat(body, instanceOf(ByteArrayOutputStream.class));
            try (PDDocument doc = Loader.loadPDF(((ByteArrayOutputStream) body).toByteArray())) {
                String text = new PDFTextStripper().getText(doc);
                assertEquals(1, doc.getNumberOfPages());
                assertThat(text, containsString(expectedText));
            } catch (IOException e) {
                throw new RuntimeCamelException(e);
            }
            return true;
        });

        template.sendBody("direct:start", expectedText);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testPdfCreationWithEncryption() throws Exception {
        final String ownerPass = "ownerPass";
        final String userPass = "userPass";
        final String expectedText = "expectedText";
        AccessPermission accessPermission = new AccessPermission();
        accessPermission.setCanPrint(false);
        StandardProtectionPolicy protectionPolicy = new StandardProtectionPolicy(ownerPass, userPass, accessPermission);
        protectionPolicy.setEncryptionKeyLength(128);

        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(exchange -> {
            Object body = exchange.getIn().getBody();
            assertThat(body, instanceOf(ByteArrayOutputStream.class));
            try (PDDocument doc = Loader.loadPDF(((ByteArrayOutputStream) body).toByteArray(), userPass)) {
                assertTrue(doc.isEncrypted(), "Expected encrypted document");
                assertFalse(doc.getCurrentAccessPermission().canPrint(), "Printing should not be permitted");
                String text = new PDFTextStripper().getText(doc);
                assertEquals(1, doc.getNumberOfPages());
                assertThat(text, containsString(expectedText));
            } catch (IOException e) {
                throw new RuntimeCamelException(e);
            }
            return true;
        });

        template.sendBodyAndHeader("direct:start",
                expectedText,
                PdfHeaderConstants.PROTECTION_POLICY_HEADER_NAME,
                protectionPolicy);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testPageSizeOfCatalog() throws Exception {
        assertMediaBox(PDRectangle.A4, "pdf:create");
        assertMediaBox(PDRectangle.A4, "pdf:create?pageSize=A4");
        assertMediaBox(PDRectangle.A5, "pdf:create?pageSize=A5");
        assertMediaBox(PDRectangle.LETTER, "pdf:create?pageSize=LETTER");
        assertMediaBox(PDRectangle.LEGAL, "pdf:create?pageSize=LEGAL");
        assertMediaBox(PDRectangle.A4, "pdf:create?pageSize=A4&textProcessingFactory=autoFormatting");
    }

    @Test
    public void testPageSizeOfOldName() throws Exception {
        assertMediaBox(PDRectangle.A1, "pdf:create?pageSize=PAGE_SIZE_A1");
        assertMediaBox(PDRectangle.LETTER, "pdf:create?pageSize=PAGE_SIZE_LETTER");
    }

    @Test
    public void testUnknownPageSize() {
        Exception e = assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("pdf:create?pageSize=B5"));
        assertThat(e.getCause().getMessage(), containsString("B5"));
        assertThat(e.getCause().getMessage(), containsString("LETTER, LEGAL, A0, A1, A2, A3, A4, A5, A6"));
    }

    @Test
    public void testCreateWithoutBody() {
        Exception e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", null));
        assertInstanceOf(InvalidPayloadException.class, e.getCause());
    }

    @Test
    public void testCreateWithLineEndsAndTab() throws Exception {
        Object body = template.requestBody("direct:create", "line one\r\nline two\rline\tthree\nline four\u0007");
        try (PDDocument doc = Loader.loadPDF(((ByteArrayOutputStream) body).toByteArray())) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text, containsString("line one"));
            assertThat(text, containsString("line two"));
            assertThat(text, containsString("line    three"));
            assertThat(text, containsString("line four"));
        }
    }

    @Test
    public void testCreateThenExtractText() {
        String text = template.requestBody("direct:createExtract", "Hello from create", String.class);
        assertThat(text, containsString("Hello from create"));
    }

    private void assertMediaBox(PDRectangle expected, String uri) throws IOException {
        Object body = template.requestBody(uri, "Some text");
        try (PDDocument doc = Loader.loadPDF(((ByteArrayOutputStream) body).toByteArray())) {
            PDRectangle mediaBox = doc.getPage(0).getMediaBox();
            assertEquals(expected.getWidth(), mediaBox.getWidth(), 0.01, uri);
            assertEquals(expected.getHeight(), mediaBox.getHeight(), 0.01, uri);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("pdf:create?fontSize=6&font=COURIER&pageSize=PAGE_SIZE_A1")
                        .to("mock:result");
                from("direct:create")
                        .to("pdf:create");
                from("direct:createExtract")
                        .to("pdf:create")
                        .to("pdf:extractText");
            }
        };
    }
}
