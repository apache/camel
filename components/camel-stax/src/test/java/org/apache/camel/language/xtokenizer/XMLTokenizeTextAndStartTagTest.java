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
package org.apache.camel.language.xtokenizer;

import java.io.StringReader;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;

import org.xml.sax.InputSource;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.builder.Namespaces;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Valid XML that xtokenize must split as an XML parser reads it: the text mode gives the text content of the element
 * (entities decoded, CDATA included), and a '>' inside an attribute value does not end the start tag in the inject and
 * unwrap modes.
 */
class XMLTokenizeTextAndStartTagTest extends CamelTestSupport {

    @Test
    void textModeDecodesEntityReferences() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:text");
        mock.expectedBodiesReceived("AT&T <5>");

        template.sendBody("direct:text", "<?xml version='1.0'?><orders><order>AT&amp;T &lt;5&gt;</order></orders>");

        mock.assertIsSatisfied();
    }

    @Test
    void textModeKeepsCdataContent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:text");
        mock.expectedBodiesReceived("a<b");

        template.sendBody("direct:text", "<?xml version='1.0'?><orders><order><![CDATA[a<b]]></order></orders>");

        mock.assertIsSatisfied();
    }

    @Test
    void textModeGroupIsXml() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:textGroup");
        mock.expectedMessageCount(1);

        template.sendBody("direct:textGroup",
                "<?xml version='1.0'?><orders><order>AT&amp;T</order><order><![CDATA[<5>]]></order></orders>");

        mock.assertIsSatisfied();
        String token = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertEquals("AT&T<5>", parse(token).getTextContent(), token);
    }

    @Test
    void injectModeKeepsAnAttributeValueWithGreaterThan() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:inject");
        mock.expectedMessageCount(1);

        template.sendBody("direct:inject",
                "<?xml version='1.0'?><orders xmlns='urn:orders'><order note='qty > 5'>1</order></orders>");

        mock.assertIsSatisfied();
        String token = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        Element order = parse(token);
        assertEquals("urn:orders", order.getNamespaceURI(), token);
        assertEquals("qty > 5", order.getAttribute("note"), token);
    }

    @Test
    void injectModeKeepsAnEmptyElementWithGreaterThanInAnAttributeValue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:inject");
        mock.expectedMessageCount(1);

        template.sendBody("direct:inject",
                "<?xml version='1.0'?><orders xmlns='urn:orders'><order note=\"a -> b\"/></orders>");

        mock.assertIsSatisfied();
        String token = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        Element order = parse(token);
        assertEquals("urn:orders", order.getNamespaceURI(), token);
        assertEquals("a -> b", order.getAttribute("note"), token);
    }

    @Test
    void injectModeDoesNotDeclareAgainAPrefixThatTheStartTagDeclares() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:inject");
        mock.expectedMessageCount(1);

        template.sendBody("direct:inject",
                "<?xml version='1.0'?><orders xmlns='urn:orders' xmlns:o-x='urn:x' xmlns:o.y='urn:y'>"
                                           + "<order xmlns:o-x='urn:x' xmlns:o.y='urn:y' o-x:id='1' o.y:id='2'>1</order></orders>");

        mock.assertIsSatisfied();
        String token = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        // a declaration added a second time is a duplicate attribute, which the parser refuses
        Element order = parse(token);
        assertEquals("urn:orders", order.getNamespaceURI(), token);
        assertEquals("1", order.getAttributeNS("urn:x", "id"), token);
        assertEquals("2", order.getAttributeNS("urn:y", "id"), token);
    }

    @Test
    void injectModeDeclaresTheDefaultNamespaceWhenAnAttributeValueContainsXmlns() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:inject");
        mock.expectedMessageCount(1);

        template.sendBody("direct:inject",
                "<?xml version='1.0'?><orders xmlns='urn:orders'><order note=\"xmlns='z'\">1</order></orders>");

        mock.assertIsSatisfied();
        String token = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        Element order = parse(token);
        assertEquals("urn:orders", order.getNamespaceURI(), token);
        assertEquals("xmlns='z'", order.getAttribute("note"), token);
    }

    @Test
    void injectModeDeclaresTheDefaultNamespaceWhenAnAttributeNameEndsWithXmlns() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:inject");
        mock.expectedMessageCount(1);

        template.sendBody("direct:inject",
                "<?xml version='1.0'?><orders xmlns='urn:orders'><order dataxmlns='v'>1</order></orders>");

        mock.assertIsSatisfied();
        String token = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        Element order = parse(token);
        assertEquals("urn:orders", order.getNamespaceURI(), token);
        assertEquals("v", order.getAttribute("dataxmlns"), token);
    }

    @Test
    void textModeDecodesCharacterReferencesAndLeavesOutCommentsAndProcessingInstructions() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:text");
        mock.expectedBodiesReceived("a<b>cd");

        template.sendBody("direct:text",
                "<?xml version='1.0'?><orders><order>a&#60;b&#x3E;c<!-- note --><?pi data?>d</order></orders>");

        mock.assertIsSatisfied();
    }

    @Test
    void textModeDoesNotResolveAnExternalEntity(@TempDir Path dir) throws Exception {
        // the file does not exist, so a reader that tried to resolve the entity would fail the exchange; the token
        // of mode t never carries the doctype, so its own reader cannot see the declaration either
        Path missing = dir.resolve("missing.txt");
        MockEndpoint mock = getMockEndpoint("mock:text");
        mock.expectedBodiesReceived("&e;");

        template.sendBody("direct:text", "<?xml version='1.0'?><!DOCTYPE orders [<!ENTITY e SYSTEM \""
                                         + missing.toUri() + "\">]><orders><order>&e;</order></orders>");

        mock.assertIsSatisfied();
    }

    @Test
    void unwrapModeWithGreaterThanInAnAttributeValue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unwrap");
        mock.expectedBodiesReceived("<id>1</id>");

        template.sendBody("direct:unwrap",
                "<?xml version='1.0'?><orders><order note='qty > 5'><id>1</id></order></orders>");

        mock.assertIsSatisfied();
    }

    private static Element parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                Namespaces none = new Namespaces();
                Namespaces orders = new Namespaces("o", "urn:orders");
                from("direct:text").split().xtokenize("//order", 't', none).to("mock:text");
                from("direct:textGroup").split().xtokenize("//order", 't', none, 2).to("mock:textGroup");
                from("direct:inject").split().xtokenize("//o:order", 'i', orders).to("mock:inject");
                from("direct:unwrap").split().xtokenize("//order", 'u', none).to("mock:unwrap");
            }
        };
    }
}
