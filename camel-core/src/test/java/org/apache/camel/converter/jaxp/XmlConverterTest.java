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
package org.apache.camel.converter.jaxp;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;

/**
 * @version $Revision$
 */
public class XmlConverterTest extends ContextTestSupport {

    public void testToResultNoSource() throws Exception {
        XmlConverter conv = new XmlConverter();
        conv.toResult(null, null);
    }

    public void testToBytesSource() throws Exception {
        XmlConverter conv = new XmlConverter();
        BytesSource bs = conv.toSource("<foo>bar</foo>".getBytes());
        assertNotNull(bs);
        assertEquals("<foo>bar</foo>", new String(bs.getData()));
    }

    public void testToStringFromSourceNoSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        Source source = null;
        String out = conv.toString(source);
        assertEquals(null, out);
    }

    public void testToStringWithBytesSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        Source source = conv.toSource("<foo>bar</foo>".getBytes());
        String out = conv.toString(source);
        assertEquals("<foo>bar</foo>", out);
    }

    public void testToByteArrayWithExchange() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        XmlConverter conv = new XmlConverter();

        Source source = conv.toSource("<foo>bar</foo>".getBytes());
        byte[] out = conv.toByteArray(source, exchange);
        assertEquals("<foo>bar</foo>", new String(out));
    }

    public void testToByteArrayWithNoExchange() throws Exception {
        XmlConverter conv = new XmlConverter();

        Source source = conv.toSource("<foo>bar</foo>".getBytes());
        byte[] out = conv.toByteArray(source, null);
        assertEquals("<foo>bar</foo>", new String(out));
    }

    public void testToDomSourceByDomSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        DOMSource source = conv.toDOMSource("<foo>bar</foo>");
        DOMSource out = conv.toDOMSource(source);
        assertSame(source, out);
    }

    public void testToDomSourceByStaxSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        SAXSource source = conv.toSAXSource("<foo>bar</foo>");
        DOMSource out = conv.toDOMSource(source);
        assertNotSame(source, out);

        assertEquals("<foo>bar</foo>", conv.toString(out));
    }

    public void testToDomSourceByCustomSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        Source dummy = new Source() {
            public String getSystemId() {
                return null;
            }

            public void setSystemId(String s) {
            }
        };

        DOMSource out = conv.toDOMSource(dummy);
        assertNull(out);
    }

    public void testToSaxSourceByInputStream() throws Exception {
        XmlConverter conv = new XmlConverter();

        InputStream is = context.getTypeConverter().convertTo(InputStream.class, "<foo>bar</foo>");
        SAXSource out = conv.toSAXSource(is);

        assertNotNull(out);
        assertEquals("<foo>bar</foo>", conv.toString(out));
    }

    public void testToSaxSourceByDomSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        DOMSource source = conv.toDOMSource("<foo>bar</foo>");
        SAXSource out = conv.toSAXSource(source);
        assertNotSame(source, out);

        assertEquals("<foo>bar</foo>", conv.toString(out));
    }

    public void testToSaxSourceByStaxSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        SAXSource source = conv.toSAXSource("<foo>bar</foo>");
        SAXSource out = conv.toSAXSource(source);
        assertSame(source, out);
    }

    public void testToSaxSourceByCustomSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        Source dummy = new Source() {
            public String getSystemId() {
                return null;
            }

            public void setSystemId(String s) {
            }
        };

        SAXSource out = conv.toSAXSource(dummy);
        assertNull(out);
    }

    public void testToStreamSourceByFile() throws Exception {
        XmlConverter conv = new XmlConverter();
        
        File file = new File("org/apache/camel/converter/stream/test.xml").getAbsoluteFile();
        StreamSource source = conv.toStreamSource(file);
        StreamSource out = conv.toStreamSource(source);
        assertSame(source, out);
    }

    public void testToStreamSourceByStreamSource() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        XmlConverter conv = new XmlConverter();

        StreamSource source = conv.toStreamSource("<foo>bar</foo>".getBytes(), exchange);
        StreamSource out = conv.toStreamSource(source);
        assertSame(source, out);
    }

    public void testToStreamSourceByDomSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        DOMSource source = conv.toDOMSource("<foo>bar</foo>");
        StreamSource out = conv.toStreamSource(source);
        assertNotSame(source, out);

        assertEquals("<foo>bar</foo>", conv.toString(out));
    }

    public void testToStreamSourceByStaxSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        SAXSource source = conv.toSAXSource("<foo>bar</foo>");
        StreamSource out = conv.toStreamSource(source);
        assertNotSame(source, out);

        assertEquals("<foo>bar</foo>", conv.toString(out));
    }

    public void testToStreamSourceByCustomSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        Source dummy = new Source() {
            public String getSystemId() {
                return null;
            }

            public void setSystemId(String s) {
            }
        };

        StreamSource out = conv.toStreamSource(dummy);
        assertNull(out);
    }

    public void testToStreamSourceByInputStream() throws Exception {
        XmlConverter conv = new XmlConverter();

        InputStream is = context.getTypeConverter().convertTo(InputStream.class, "<foo>bar</foo>");
        StreamSource out = conv.toStreamSource(is);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", conv.toString(out));
    }

    public void testToStreamSourceByReader() throws Exception {
        XmlConverter conv = new XmlConverter();

        Reader reader = context.getTypeConverter().convertTo(Reader.class, "<foo>bar</foo>");
        StreamSource out = conv.toStreamSource(reader);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", conv.toString(out));
    }

    public void testToStreamSourceByByteArray() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        XmlConverter conv = new XmlConverter();

        byte[] bytes = context.getTypeConverter().convertTo(byte[].class, "<foo>bar</foo>");
        StreamSource out = conv.toStreamSource(bytes, exchange);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", conv.toString(out));
    }

    public void testToStreamSourceByByteBuffer() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        XmlConverter conv = new XmlConverter();

        ByteBuffer bytes = context.getTypeConverter().convertTo(ByteBuffer.class, "<foo>bar</foo>");
        StreamSource out = conv.toStreamSource(bytes, exchange);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", conv.toString(out));
    }

    public void testToVariousUsingNull() throws Exception {
        XmlConverter conv = new XmlConverter();

        InputStream is = null;
        assertNull(conv.toStreamSource(is));

        Reader reader = null;
        assertNull(conv.toStreamSource(reader));

        File file = null;
        assertNull(conv.toStreamSource(file));

        byte[] bytes = null;
        assertNull(conv.toStreamSource(bytes, null));

        try {
            Node node = null;
            conv.toDOMElement(node);
            fail("Should have thrown exception");
        } catch (TransformerException e) {
            // expected
        }
    }

    public void testToReaderFromSource() throws Exception {
        XmlConverter conv = new XmlConverter();
        SAXSource source = conv.toSAXSource("<foo>bar</foo>");
        
        Reader out = conv.toReaderFromSource(source);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", context.getTypeConverter().convertTo(String.class, out));
    }

    public void testToDomSourceFromInputStream() throws Exception {
        XmlConverter conv = new XmlConverter();

        InputStream is = context.getTypeConverter().convertTo(InputStream.class, "<foo>bar</foo>");
        DOMSource out = conv.toDOMSource(is);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", context.getTypeConverter().convertTo(String.class, out));
    }

    public void testToDomElement() throws Exception {
        XmlConverter conv = new XmlConverter();
        SAXSource source = conv.toSAXSource("<foo>bar</foo>");

        Element out = conv.toDOMElement(source);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", context.getTypeConverter().convertTo(String.class, out));
    }

    public void testToDomElementFromDocumentNode() throws Exception {
        XmlConverter conv = new XmlConverter();
        Document doc = context.getTypeConverter().convertTo(Document.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");

        Element out = conv.toDOMElement(doc);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", context.getTypeConverter().convertTo(String.class, out));
    }

    public void testToDomElementFromElementNode() throws Exception {
        XmlConverter conv = new XmlConverter();
        Document doc = context.getTypeConverter().convertTo(Document.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");

        Element out = conv.toDOMElement(doc.getDocumentElement());
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", context.getTypeConverter().convertTo(String.class, out));
    }

    public void testToDocumentFromBytes() throws Exception {
        XmlConverter conv = new XmlConverter();
        byte[] bytes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>".getBytes();

        Document out = conv.toDOMDocument(bytes);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", context.getTypeConverter().convertTo(String.class, out));
    }

    public void testToDocumentFromInputStream() throws Exception {
        XmlConverter conv = new XmlConverter();
        InputStream is = context.getTypeConverter().convertTo(InputStream.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");

        Document out = conv.toDOMDocument(is);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", context.getTypeConverter().convertTo(String.class, out));
    }

    public void testToInputStreamFromDocument() throws Exception {
        XmlConverter conv = new XmlConverter();
        Document doc = context.getTypeConverter().convertTo(Document.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");

        InputStream is = conv.toInputStrean(doc);
        assertNotNull(is);
        assertEquals("<foo>bar</foo>", context.getTypeConverter().convertTo(String.class, is));
    }

    public void testToDocumentFromFile() throws Exception {
        XmlConverter conv = new XmlConverter();
        File file = new File("./src/test/resources/org/apache/camel/converter/stream/test.xml").getAbsoluteFile();

        Document out = conv.toDOMDocument(file);
        assertNotNull(out);
        String s = context.getTypeConverter().convertTo(String.class, out);
        assertTrue(s.contains("<firstName>James</firstName>"));
    }

    public void testToInputStreamByDomSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        DOMSource source = conv.toDOMSource("<foo>bar</foo>");
        InputStream out = conv.toInputStrean(source);
        assertNotSame(source, out);

        String s = context.getTypeConverter().convertTo(String.class, out);
        assertEquals("<foo>bar</foo>", s);
    }

    public void testToInputSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        InputStream is = context.getTypeConverter().convertTo(InputStream.class, "<foo>bar</foo>");
        InputSource out = conv.toInputSource(is);
        assertNotNull(out);
        assertNotNull(out.getByteStream());
    }

}
