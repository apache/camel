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
package org.apache.camel.component.fop;

import java.io.StringReader;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;

import org.xml.sax.InputSource;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The bodies the FOP producer accepts, and a clear error for the ones it cannot render.
 */
public class FopBodyTest extends CamelTestSupport {

    @Test
    public void renderDomDocument() throws Exception {
        Document document = parse(FopHelper.decorateTextWithXSLFO("Test Content"));

        String text = template.requestBody("direct:start", document, String.class);
        assertEquals("Test Content", text.trim());
    }

    @Test
    public void renderDomSource() throws Exception {
        DOMSource source = new DOMSource(parse(FopHelper.decorateTextWithXSLFO("Test Content")));

        String text = template.requestBody("direct:start", source, String.class);
        assertEquals("Test Content", text.trim());
    }

    @Test
    public void nullBodyFailsWithInvalidPayload() {
        CamelExecutionException e
                = assertThrows(CamelExecutionException.class, () -> template.requestBody("direct:start", (Object) null));
        assertInstanceOf(InvalidPayloadException.class, e.getCause());
    }

    @Test
    public void mapBodyFailsWithInvalidPayload() {
        CamelExecutionException e
                = assertThrows(CamelExecutionException.class,
                        () -> template.requestBody("direct:start", Map.of("text", "Test Content")));
        assertInstanceOf(InvalidPayloadException.class, e.getCause());
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("fop:txt");
            }
        };
    }
}
