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

import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.component.cxf.CxfPayload;
import org.apache.camel.test.junit4.ExchangeTestSupport;
import org.apache.cxf.staxutils.StaxSource;
import org.apache.cxf.staxutils.StaxUtils;
import org.junit.Test;

public class CachedCxfPayloadTest extends ExchangeTestSupport {
    private static final String PAYLOAD = "<foo>bar<![CDATA[ & a cdata section ]]></foo>";
    private static final String PAYLOAD_AMPED = "<foo>bar &amp; a cdata section </foo>";

    @Test
    public void testCachedCxfPayloadSAXSource() throws TypeConversionException, NoTypeConversionAvailableException, IOException {
        SAXSource source = context.getTypeConverter().mandatoryConvertTo(SAXSource.class, PAYLOAD);
        // this conversion uses org.apache.camel.converter.jaxp.XmlConverter.toDOMNodeFromSAX which uses Transformer
        // to convert SAXSource to DOM. This conversion preserves the content but loses its original representation.
        doTest(source, PAYLOAD_AMPED);
    }

    @Test
    public void testCachedCxfPayloadStAXSource() throws TypeConversionException, NoTypeConversionAvailableException, IOException {
        StAXSource source = context.getTypeConverter().mandatoryConvertTo(StAXSource.class, PAYLOAD);
        doTest(source, PAYLOAD);
    }

    @Test
    public void testCachedCxfPayloadStaxSource() throws TypeConversionException, NoTypeConversionAvailableException, IOException {
        XMLStreamReader streamReader = StaxUtils.createXMLStreamReader(new StreamSource(new StringReader(PAYLOAD)));
        StaxSource source = new StaxSource(streamReader);
        doTest(source, PAYLOAD);
    }

    @Test
    public void testCachedCxfPayloadDOMSource() throws TypeConversionException, NoTypeConversionAvailableException, IOException {
        DOMSource source = context.getTypeConverter().mandatoryConvertTo(DOMSource.class, PAYLOAD);
        doTest(source, PAYLOAD);
    }

    @Test
    public void testCachedCxfPayloadStreamSource() throws TypeConversionException, NoTypeConversionAvailableException, IOException {
        StreamSource source = context.getTypeConverter().mandatoryConvertTo(StreamSource.class, PAYLOAD);
        doTest(source, PAYLOAD);
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
