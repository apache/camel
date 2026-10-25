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

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.pdf.PDFUtil.textToPDF;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PdfConverterTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Test
    public void testConvertByteArray() throws Exception {
        final String originalText = "Test";

        byte[] array;
        try (PDDocument document = textToPDF(originalText); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            document.save(baos);
            array = baos.toByteArray();
        }

        try (PDDocument doc = context.getTypeConverter().convertTo(PDDocument.class, array)) {
            assertNotNull(doc);
            assertEquals(1, doc.getNumberOfPages());
        }

        resultEndpoint.setExpectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(exchange -> {
            assertThat(exchange.getIn().getBody(String.class), containsString(originalText));
            return true;
        });
        template.sendBody("direct:start", array);
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
