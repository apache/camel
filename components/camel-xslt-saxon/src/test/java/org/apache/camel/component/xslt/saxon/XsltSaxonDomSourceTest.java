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
package org.apache.camel.component.xslt.saxon;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.xml.sax.InputSource;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.TypeConverterSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XsltSaxonDomSourceTest {

    private static final String ENDPOINT = "xslt-saxon:org/apache/camel/component/xslt/saxon/dom-source-root.xsl";
    private static final String INPUT = """
            <req:Request xmlns:req="urn:request">
              <req:Header>SOURCE_SYSTEM_VALUE</req:Header>
              <req:Authentication>
                <req:UserName>test-user</req:UserName>
                <req:Password>test-password</req:Password>
              </req:Authentication>
            </req:Request>
            """;

    private DefaultCamelContext context;
    private ProducerTemplate template;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        // Force the DOM and generic Source fallback paths without depending on converter discovery order.
        TypeConverterSupport converter = new TypeConverterSupport() {
            @Override
            public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
                Payload payload = (Payload) value;
                if (type == Source.class || type == payload.source.getClass() && !payload.genericSourceOnly) {
                    payload.conversions++;
                    return type.cast(payload.source);
                }
                return null;
            }
        };
        for (Class<?> type : new Class<?>[] {
                Source.class, DOMSource.class, SAXSource.class,
                StreamSource.class, StAXSource.class }) {
            context.getTypeConverterRegistry().addTypeConverter(type, Payload.class, converter);
        }
        context.start();
        template = context.createProducerTemplate();
        template.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (template != null) {
            template.stop();
        }
        if (context != null) {
            context.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void testImplicitDocumentElement(boolean genericSourceOnly) throws Exception {
        Document document = parse(INPUT);
        Document original = (Document) document.cloneNode(true);
        Element element = document.getDocumentElement();
        DOMSource source = new DOMSource(element, "file:/payload/request.xml");
        Payload payload = new Payload(source, genericSourceOnly);
        // Reuse the converter's cached DOMSource, as an Element-backed CXF payload does.
        for (int i = 0; i < 3; i++) {
            String result = template.requestBody(ENDPOINT, payload, String.class);
            assertTrue(result.contains("<root"), result);
            Document output = parse(result);
            assertEquals("root", output.getDocumentElement().getNodeName());
            assertEquals("test-user", output.getElementsByTagNameNS("urn:login", "UserId").item(0).getTextContent());
            assertEquals("test-password", output.getElementsByTagNameNS("urn:login", "Password").item(0).getTextContent());
            assertEquals(1, output.getElementsByTagNameNS("urn:login", "LogIn_Input").getLength());
            assertFalse(result.contains("SOURCE_SYSTEM_VALUE"), result);
        }
        assertTrue(payload.conversions >= 3, "The test must use the registered DOM converter");
        assertSame(element, source.getNode());
        assertSame(document, element.getOwnerDocument());
        assertSame(document, element.getParentNode());
        assertTrue(original.isEqualNode(document), "Conversion must not modify the original DOM");
        Source prepared = getSource(payload);
        assertNotSame(source, prepared);
        assertSame(document, ((DOMSource) prepared).getNode());
        assertEquals(source.getSystemId(), prepared.getSystemId());
        assertEquals("file:/payload/request.xml", source.getSystemId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void testStaxLookupReturningDomSource(boolean sourceExpression) throws Exception {
        Document document = parse(INPUT);
        Document original = (Document) document.cloneNode(true);
        DOMSource source = new DOMSource(document.getDocumentElement(), "file:/payload/request.xml");
        GenericSourcePayload payload = new GenericSourcePayload(source);
        TypeConverterSupport converter = new TypeConverterSupport() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
                GenericSourcePayload input = (GenericSourcePayload) value;
                input.requestedType = type;
                return (T) input.source;
            }
        };
        context.getTypeConverterRegistry().addTypeConverter(Source.class, GenericSourcePayload.class, converter);
        // Pin the generic Source converter selected by the registry for a StAX request, as with CXF payloads.
        context.getTypeConverterRegistry().addTypeConverter(StAXSource.class, GenericSourcePayload.class, converter);
        for (int i = 0; i < 3; i++) {
            String result = sourceExpression
                    ? template.requestBodyAndHeader(
                            ENDPOINT + "?source=header:payloadSource", "unused body", "payloadSource", payload, String.class)
                    : template.requestBody(ENDPOINT, payload, String.class);
            assertSame(StAXSource.class, payload.requestedType, "Must exercise Saxon's non-null StAX conversion path");
            assertEquals("root", parse(result).getDocumentElement().getNodeName(), result);
            assertFalse(result.contains("SOURCE_SYSTEM_VALUE"), result);
        }
        Source prepared = getSource(payload);
        assertSame(document, ((DOMSource) prepared).getNode());
        assertNotSame(source, prepared);
        assertEquals(source.getSystemId(), prepared.getSystemId());
        assertSame(document.getDocumentElement(), source.getNode());
        assertSame(document, source.getNode().getParentNode());
        assertTrue(original.isEqualNode(document));
    }

    @ParameterizedTest
    @ValueSource(strings = { "element", "nested", "detached" })
    void testExplicitElementSource(String kind) throws Exception {
        DOMSource source = elementSource(kind);
        assertSame(source, getSource(source));
        assertElementContext(template.requestBody(ENDPOINT, source, String.class));
    }

    @Test
    void testExplicitSourceExpression() throws Exception {
        DOMSource source = elementSource("element");
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(new Payload(source, false));
        exchange.getMessage().setHeader("payloadSource", source);
        Expression expression = ExpressionBuilder.headerExpression("payloadSource");
        assertSame(source, new SaxonXmlSourceHandlerFactoryImpl().getSource(exchange, expression));
        String result = template.requestBodyAndHeader(
                ENDPOINT + "?source=header:payloadSource", new Payload(source, false), "payloadSource", source, String.class);
        assertElementContext(result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "nested", "detached" })
    void testImplicitSubtree(String kind) throws Exception {
        DOMSource source = elementSource(kind);
        Element element = (Element) source.getNode();
        Document document = element.getOwnerDocument();
        Document original = (Document) document.cloneNode(true);
        var parent = element.getParentNode();
        Payload payload = new Payload(source, false);
        assertSame(source, getSource(payload));
        assertElementContext(template.requestBody(ENDPOINT, payload, String.class));
        assertSame(element, source.getNode());
        assertSame(parent, element.getParentNode());
        assertSame(document, element.getOwnerDocument());
        assertTrue(original.isEqualNode(document));
    }

    @ParameterizedTest
    @ValueSource(strings = { "document", "sax", "stream", "stax" })
    void testExplicitDocumentAndStreamingSources(String kind) throws Exception {
        Source source = documentOrStreamingSource(kind);
        assertSame(source, getSource(source));
        String result = template.requestBody(ENDPOINT, source, String.class);
        assertTrue(result.contains("<root"), result);
        assertEquals("root", parse(result).getDocumentElement().getNodeName());
    }

    @ParameterizedTest
    @ValueSource(strings = { "document", "sax", "stream", "stax" })
    void testImplicitDocumentAndStreamingSources(String kind) throws Exception {
        Source source = documentOrStreamingSource(kind);
        Payload payload = new Payload(source, false);
        assertSame(source, getSource(payload));
        String result = template.requestBody(ENDPOINT, payload, String.class);
        assertEquals("root", parse(result).getDocumentElement().getNodeName());
        assertTrue(payload.conversions >= 2);
    }

    private static Source documentOrStreamingSource(String kind) throws Exception {
        return switch (kind) {
            case "document" -> new DOMSource(parse(INPUT));
            case "sax" -> new SAXSource(new InputSource(new StringReader(INPUT)));
            case "stream" -> new StreamSource(new StringReader(INPUT));
            case "stax" -> new StAXSource(XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(INPUT)));
            default -> throw new IllegalArgumentException(kind);
        };
    }

    private Source getSource(Object body) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        return new SaxonXmlSourceHandlerFactoryImpl().getSource(exchange, (Expression) null);
    }

    private static void assertElementContext(String result) throws Exception {
        assertEquals("element-context", parse(result).getDocumentElement().getNodeName(), result);
        assertFalse(result.contains("OUTSIDE_PAYLOAD"), result);
        assertFalse(result.contains("UNRELATED"), result);
    }

    private static DOMSource elementSource(String kind) throws Exception {
        Document document = parse(INPUT);
        Element element = document.getDocumentElement();
        if (kind.equals("nested")) {
            document = parse("<envelope>OUTSIDE_PAYLOAD" + INPUT + "<other>UNRELATED</other></envelope>");
            element = (Element) document.getDocumentElement().getElementsByTagNameNS("urn:request", "Request").item(0);
        } else if (kind.equals("detached")) {
            document.removeChild(element);
        }
        return new DOMSource(element);
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static final class GenericSourcePayload {
        private final DOMSource source;
        private Class<?> requestedType;

        private GenericSourcePayload(DOMSource source) {
            this.source = source;
        }
    }

    private static final class Payload {
        private final Source source;
        private final boolean genericSourceOnly;
        private int conversions;

        private Payload(Source source, boolean genericSourceOnly) {
            this.source = source;
            this.genericSourceOnly = genericSourceOnly;
        }
    }
}
