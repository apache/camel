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
package org.apache.camel.component.cxf.converter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;

import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.StreamCache;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.cxf.common.CxfPayload;
import org.apache.camel.language.xpath.XPathBuilder;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.apache.cxf.staxutils.StaxSource;
import org.apache.cxf.staxutils.StaxUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CachedCxfPayloadTest extends ExchangeTestSupport {
    private static final String PAYLOAD = "<foo>bar<![CDATA[ & a cdata section ]]></foo>";
    private static final String PAYLOAD_AMPED = "<foo>bar &amp; a cdata section </foo>";
    private static final String NS_PAYLOAD_NAMESPACE = "urn:camel:test";
    private static final String NS_PAYLOAD = "<foo xmlns=\"" + NS_PAYLOAD_NAMESPACE + "\"><bar>baz</bar></foo>";

    @Test
    public void testCachedCxfPayloadSAXSource()
            throws TypeConversionException, NoTypeConversionAvailableException, IOException {
        SAXSource source = context.getTypeConverter().mandatoryConvertTo(SAXSource.class, PAYLOAD);
        // this conversion uses org.apache.camel.converter.jaxp.XmlConverter.toDOMNodeFromSAX which uses Transformer
        // to convert SAXSource to DOM. This conversion preserves the content but loses its original representation.
        doTest(source, PAYLOAD_AMPED);
    }

    @Test
    public void testCachedCxfPayloadStAXSource()
            throws TypeConversionException, NoTypeConversionAvailableException, IOException {
        StAXSource source = context.getTypeConverter().mandatoryConvertTo(StAXSource.class, PAYLOAD);
        doTest(source, PAYLOAD);
    }

    @Test
    public void testCachedCxfPayloadStaxSource()
            throws TypeConversionException, NoTypeConversionAvailableException, IOException {
        XMLStreamReader streamReader = StaxUtils.createXMLStreamReader(new StreamSource(new StringReader(PAYLOAD)));
        StaxSource source = new StaxSource(streamReader);
        doTest(source, PAYLOAD);
    }

    @Test
    public void testCachedCxfPayloadDOMSource()
            throws TypeConversionException, NoTypeConversionAvailableException, IOException {
        DOMSource source = context.getTypeConverter().mandatoryConvertTo(DOMSource.class, PAYLOAD);
        doTest(source, PAYLOAD);
    }

    @Test
    public void testCachedCxfPayloadStreamSource()
            throws TypeConversionException, NoTypeConversionAvailableException, IOException {
        StreamSource source = context.getTypeConverter().mandatoryConvertTo(StreamSource.class, PAYLOAD);
        doTest(source, PAYLOAD);
    }

    @Test
    public void testCachedCxfPayloadUsesTheStreamCacheConverters() {
        TypeConverterRegistry registry = context.getTypeConverterRegistry();
        // CAMEL-25231: a CachedCxfPayload is both a StreamCache and a CxfPayload, and both have converters to the XML
        // sources. The interface is tried before the super class, so the StreamCache converters must always be chosen,
        // whatever the order of the converters in the registry, which can change between JVM runs
        for (Class<?> type : List.of(Source.class, SAXSource.class, DOMSource.class, StAXSource.class,
                StreamSource.class, Document.class)) {
            TypeConverter fromStreamCache = registry.lookup(type, StreamCache.class);
            assertNotNull(fromStreamCache, "no converter from StreamCache to " + type.getName());
            assertNotSame(registry.lookup(type, CxfPayload.class), fromStreamCache);
            assertSame(fromStreamCache, registry.lookup(type, CachedCxfPayload.class), type.getName());
        }
    }

    @Test
    public void testCachedCxfPayloadToDOMSourceHoldsTheDocument() throws Exception {
        // CAMEL-25231: the DOMSource must hold the document, not its document element, as the xslt, xquery and
        // validator components then evaluate from another context node (see CAMEL-25224)
        for (CachedCxfPayload<?> cache : List.of(cachedDomPayload(), cachedStaxPayload())) {
            DOMSource source = context.getTypeConverter().mandatoryConvertTo(DOMSource.class, exchange, cache);
            Document document = assertInstanceOf(Document.class, source.getNode());
            assertEquals("foo", document.getDocumentElement().getLocalName());
        }
    }

    @Test
    public void testXPathOnCachedCxfPayload() throws Exception {
        for (CachedCxfPayload<?> cache : List.of(cachedDomPayload(), cachedStaxPayload())) {
            exchange.getIn().setBody(cache);
            String value = XPathBuilder.xpath("/x:foo/x:bar").namespace("x", NS_PAYLOAD_NAMESPACE).stringResult()
                    .evaluate(exchange, String.class);
            assertEquals("baz", value);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private CachedCxfPayload<?> cachedDomPayload() throws Exception {
        List<Source> body = new ArrayList<>();
        body.add(new DOMSource(StaxUtils.read(new StringReader(NS_PAYLOAD)).getDocumentElement()));
        return new CachedCxfPayload(new CxfPayload(null, body, null), exchange);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private CachedCxfPayload<?> cachedStaxPayload() {
        List<Source> body = new ArrayList<>();
        body.add(new StaxSource(StaxUtils.createXMLStreamReader(new StreamSource(new StringReader(NS_PAYLOAD)))));
        return new CachedCxfPayload(new CxfPayload(null, body, null), exchange);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void doTest(Object source, String payload) throws IOException {
        CxfPayload<?> originalPayload = context.getTypeConverter().convertTo(CxfPayload.class, source);
        CachedCxfPayload<?> cache = new CachedCxfPayload(originalPayload, exchange);

        assertTrue(cache.inMemory());

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        cache.writeTo(bos);

        String s = context.getTypeConverter().convertTo(String.class, bos);
        assertEquals(payload, s);

        cache.reset();

        CachedCxfPayload clone = (CachedCxfPayload) cache.copy(exchange);
        bos = new ByteArrayOutputStream();
        clone.writeTo(bos);

        s = context.getTypeConverter().convertTo(String.class, bos);
        assertEquals(payload, s);

        cache.reset();
        clone.reset();

        s = context.getTypeConverter().convertTo(String.class, cache);
        assertEquals(payload, s);

        s = context.getTypeConverter().convertTo(String.class, clone);
        assertEquals(payload, s);
    }
}
