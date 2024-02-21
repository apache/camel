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
import java.io.IOException;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PdfCreationTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testPdfCreation() throws Exception {
        final String expectedText = "expectedText";
        template.sendBody("direct:start", expectedText);
        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                Object body = exchange.getIn().getBody();
                assertThat(body, instanceOf(ByteArrayOutputStream.class));
                try {
                    PDDocument doc = Loader.loadPDF(
                            new RandomAccessReadBuffer(new ByteArrayInputStream(((ByteArrayOutputStream) body).toByteArray())));
                    PDFTextStripper pdfTextStripper = new PDFTextStripper();
                    String text = pdfTextStripper.getText(doc);
                    assertEquals(1, doc.getNumberOfPages());
                    assertThat(text, containsString(expectedText));
                } catch (IOException e) {
                    throw new RuntimeCamelException(e);
                }
                return true;
            }
        });
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
        template.sendBodyAndHeader("direct:start",
                expectedText,
                PdfHeaderConstants.PROTECTION_POLICY_HEADER_NAME,
                protectionPolicy);

        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                Object body = exchange.getIn().getBody();
                assertThat(body, instanceOf(ByteArrayOutputStream.class));
                try {
                    PDDocument doc
                            = Loader.loadPDF(new RandomAccessReadBuffer(
                                    new ByteArrayInputStream(((ByteArrayOutputStream) body).toByteArray())), userPass);
                    assertTrue(doc.isEncrypted(), "Expected encrypted document");
                    assertFalse(doc.getCurrentAccessPermission().canPrint(), "Printing should not be permitted");
                    PDFTextStripper pdfTextStripper = new PDFTextStripper();
                    String text = pdfTextStripper.getText(doc);
                    assertEquals(1, doc.getNumberOfPages());
                    assertThat(text, containsString(expectedText));
                } catch (Exception e) {
                    throw new RuntimeCamelException(e);
                }
                return true;
            }
        });
        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("pdf:create?fontSize=6&font=COURIER&pageSize=PAGE_SIZE_A1")
                        .to("mock:result");
            }
        };
    }
}
