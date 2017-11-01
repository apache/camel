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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardDecryptionMaterial;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;

public class PdfTextExtractionTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @Test
    public void testExtractText() throws Exception {
        final String expectedText = "Test string";
        PDDocument document = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        PDPageContentStream contentStream = new PDPageContentStream(document, page);
        contentStream.setFont(PDType1Font.HELVETICA, 12);
        contentStream.beginText();
        contentStream.moveTextPositionByAmount(20, 400);
        contentStream.drawString(expectedText);
        contentStream.endText();
        contentStream.close();

        template.sendBody("direct:start", document);

        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                Object body = exchange.getIn().getBody();
                assertThat(body, instanceOf(String.class));
                assertThat((String) body, containsString(expectedText));
                return true;
            }
        });
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
        PDDocument document = new PDDocument();

        final String expectedText = "Test string";
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        PDPageContentStream contentStream = new PDPageContentStream(document, page);
        contentStream.setFont(PDType1Font.HELVETICA, 12);
        contentStream.beginText();
        contentStream.moveTextPositionByAmount(20, 400);
        contentStream.drawString(expectedText);
        contentStream.endText();
        contentStream.close();

        document.protect(protectionPolicy);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output);

        // Encryption happens after saving.
        PDDocument encryptedDocument = PDDocument.load(new ByteArrayInputStream(output.toByteArray()), userPass);

        template.sendBodyAndHeader("direct:start",
                encryptedDocument,
                PdfHeaderConstants.DECRYPTION_MATERIAL_HEADER_NAME,
                new StandardDecryptionMaterial(userPass));

        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                Object body = exchange.getIn().getBody();
                assertThat(body, instanceOf(String.class));
                assertThat((String) body, containsString(expectedText));
                return true;
            }
        });
        resultEndpoint.assertIsSatisfied();
        document.isEncrypted();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("pdf:extractText")
                        .to("mock:result");
            }
        };
    }
}
