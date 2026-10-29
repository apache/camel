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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        assertPreparedSource(kind, getSource(source));
        String result = template.requestBody(ENDPOINT, source, String.class);
        assertTrue(result.contains("<root"), result);
        assertEquals("root", parse(result).getDocumentElement().getNodeName());
    }

    @ParameterizedTest
    @ValueSource(strings = { "document", "sax", "stream", "stax" })
    void testImplicitDocumentAndStreamingSources(String kind) throws Exception {
        Source source = documentOrStreamingSource(kind);
        Payload payload = new Payload(source, false);
        assertPreparedSource(kind, getSource(payload));
        String result = template.requestBody(ENDPOINT, payload, String.class);
        assertEquals("root", parse(result).getDocumentElement().getNodeName());
        assertTrue(payload.conversions >= 2);
    }

    @Test
    void testStreamSourceUnwrappedWhenSecureProcessingDisabled() throws Exception {
        XsltSaxonEndpoint endpoint = context.getEndpoint(ENDPOINT + "?secureProcessing=false", XsltSaxonEndpoint.class);
        endpoint.start();
        XsltSaxonBuilder builder = (XsltSaxonBuilder) endpoint.getXslt();
        Exchange exchange = new DefaultExchange(context);
        StreamSource source = new StreamSource(new StringReader(INPUT));
        exchange.getMessage().setBody(source);
        assertSame(source, builder.getSourceHandlerFactory().getSource(exchange, (Expression) null));
    }

    /**
     * A source that is already parsed ({@code DOMSource}) or read through a reader the caller controls
     * ({@code StAXSource}) is passed through unchanged; a stream-based source still to be parsed is wrapped with a
     * reader that does not resolve references to external resources.
     */
    private static void assertPreparedSource(String kind, Source prepared) throws Exception {
        if (kind.equals("document")) {
            assertInstanceOf(DOMSource.class, prepared);
        } else if (kind.equals("stax")) {
            assertInstanceOf(StAXSource.class, prepared);
        } else {
            SAXSource saxSource = assertInstanceOf(SAXSource.class, prepared);
            assertNotNull(saxSource.getXMLReader(), "A restricted reader should be installed on the source");
            assertFalse(saxSource.getXMLReader().getFeature("http://xml.org/sax/features/external-general-entities"));
        }
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
