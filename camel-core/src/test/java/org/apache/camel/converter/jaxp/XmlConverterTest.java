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
import java.util.Properties;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import org.apache.camel.BytesSource;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;

/**
 * @version 
 */
public class XmlConverterTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/xml");
        super.setUp();
    }

    public void testToResultNoSource() throws Exception {
        XmlConverter conv = new XmlConverter();
        conv.toResult(null, null);
    }

    public void testToBytesSource() throws Exception {
        XmlConverter conv = new XmlConverter();
        BytesSource bs = conv.toBytesSource("<foo>bar</foo>".getBytes());
        assertNotNull(bs);
        assertEquals("<foo>bar</foo>", new String(bs.getData()));
    }

    public void testToStringFromSourceNoSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        Source source = null;
        String out = conv.toString(source, null);
        assertEquals(null, out);
    }

    public void testToStringWithBytesSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        Source source = conv.toBytesSource("<foo>bar</foo>".getBytes());
        String out = conv.toString(source, null);
        assertEquals("<foo>bar</foo>", out);
    }

    public void testToStringWithDocument() throws Exception {
        XmlConverter conv = new XmlConverter();

        Document document = conv.createDocument();
        Element foo = document.createElement("foo");
        foo.setTextContent("bar");
        document.appendChild(foo);

        String out = conv.toStringFromDocument(document, null);
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><foo>bar</foo>", out);
    }

    public void testToStringWithDocumentSourceOutputProperties() throws Exception {
        XmlConverter conv = new XmlConverter();

        Document document = conv.createDocument();
        Element foo = document.createElement("foo");
        foo.setTextContent("bar");
        document.appendChild(foo);

        Properties properties = new Properties();
        properties.put(OutputKeys.ENCODING, "ISO-8859-1");

        String out = conv.toStringFromDocument(document, properties);
        assertEquals("<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"no\"?><foo>bar</foo>", out);
    }

    public void testToSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        Source source = conv.toSource("<foo>bar</foo>");
        String out = conv.toString(source, null);
        assertEquals("<foo>bar</foo>", out);
    }

    public void testToSourceUsingTypeConverter() throws Exception {
        Source source = context.getTypeConverter().convertTo(Source.class, "<foo>bar</foo>");
        String out = context.getTypeConverter().convertTo(String.class, source);
        assertEquals("<foo>bar</foo>", out);

        // try again to ensure it works the 2nd time
        source = context.getTypeConverter().convertTo(Source.class, "<foo>baz</foo>");
        out = context.getTypeConverter().convertTo(String.class, source);
        assertEquals("<foo>baz</foo>", out);
    }

    public void testToByteArrayWithExchange() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        XmlConverter conv = new XmlConverter();

        Source source = conv.toBytesSource("<foo>bar</foo>".getBytes());
        byte[] out = conv.toByteArray(source, exchange);
        assertEquals("<foo>bar</foo>", new String(out));
    }

    public void testToByteArrayWithNoExchange() throws Exception {
        XmlConverter conv = new XmlConverter();

        Source source = conv.toBytesSource("<foo>bar</foo>".getBytes());
        byte[] out = conv.toByteArray(source, null);
        assertEquals("<foo>bar</foo>", new String(out));
    }

    public void testToDomSourceByDomSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        DOMSource source = conv.toDOMSource("<foo>bar</foo>");
        DOMSource out = conv.toDOMSource(source);
        assertSame(source, out);
    }

    public void testToDomSourceByByteArray() throws Exception {
        XmlConverter conv = new XmlConverter();

        byte[] bytes = "<foo>bar</foo>".getBytes();
        DOMSource source = conv.toDOMSource(bytes);
        assertNotNull(source);

        byte[] out = conv.toByteArray(source, null);
        assertEquals(new String(bytes), new String(out));
    }

    public void testToDomSourceBySaxSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        SAXSource source = conv.toSAXSource("<foo>bar</foo>", null);
        DOMSource out = conv.toDOMSource(source);
        assertNotSame(source, out);

        assertEquals("<foo>bar</foo>", conv.toString(out, null));
    }
    
    public void testToDomSourceByStAXSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        // because of https://bugs.openjdk.java.net/show_bug.cgi?id=100228, we have to set the XML version explicitly
        StAXSource source = conv.toStAXSource("<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>", null);
        DOMSource out = conv.toDOMSource(source);
        assertNotSame(source, out);

        assertEquals("<foo>bar</foo>", conv.toString(out, null));
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
        SAXSource out = conv.toSAXSource(is, null);

        assertNotNull(out);
        assertEquals("<foo>bar</foo>", conv.toString(out, null));
    }
    public void testToStAXSourceByInputStream() throws Exception {
        XmlConverter conv = new XmlConverter();

        InputStream is = context.getTypeConverter().convertTo(InputStream.class, "<foo>bar</foo>");
        StAXSource out = conv.toStAXSource(is, null);

        assertNotNull(out);
        assertEquals("<foo>bar</foo>", conv.toString(out, null));
    }

    public void testToSaxSourceFromFile() throws Exception {
        XmlConverter conv = new XmlConverter();

        template.sendBodyAndHeader("file:target/xml", "<foo>bar</foo>", Exchange.FILE_NAME, "myxml.xml");
        File file = new File("target/xml/myxml.xml");

        SAXSource out = conv.toSAXSource(file, null);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", context.getTypeConverter().convertTo(String.class, out));
    }
    public void testToStAXSourceFromFile() throws Exception {
        XmlConverter conv = new XmlConverter();

        template.sendBodyAndHeader("file:target/xml", "<foo>bar</foo>", Exchange.FILE_NAME, "myxml.xml");
        File file = new File("target/xml/myxml.xml");

        StAXSource out = conv.toStAXSource(file, null);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", context.getTypeConverter().convertTo(String.class, out));
    }

    public void testToSaxSourceByDomSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        DOMSource source = conv.toDOMSource("<foo>bar</foo>");
        SAXSource out = conv.toSAXSource(source, null);
        assertNotSame(source, out);

        assertEquals("<foo>bar</foo>", conv.toString(out, null));
    }

    public void testToSaxSourceBySaxSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        SAXSource source = conv.toSAXSource("<foo>bar</foo>", null);
        SAXSource out = conv.toSAXSource(source, null);
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

        SAXSource out = conv.toSAXSource(dummy, null);
        assertNull(out);
    }

    public void testToStreamSourceByFile() throws Exception {
        XmlConverter conv = new XmlConverter();
        
        File file = new File("org/apache/camel/converter/stream/test.xml");
        StreamSource source = conv.toStreamSource(file);
        StreamSource out = conv.toStreamSource(source, null);
        assertSame(source, out);
    }

    public void testToStreamSourceByStreamSource() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        XmlConverter conv = new XmlConverter();

        StreamSource source = conv.toStreamSource("<foo>bar</foo>".getBytes(), exchange);
        StreamSource out = conv.toStreamSource(source, null);
        assertSame(source, out);
    }

    public void testToStreamSourceByDomSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        DOMSource source = conv.toDOMSource("<foo>bar</foo>");
        StreamSource out = conv.toStreamSource(source, null);
        assertNotSame(source, out);

        assertEquals("<foo>bar</foo>", conv.toString(out, null));
    }

    public void testToStreamSourceBySaxSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        SAXSource source = conv.toSAXSource("<foo>bar</foo>", null);
        StreamSource out = conv.toStreamSource(source, null);
        assertNotSame(source, out);

        assertEquals("<foo>bar</foo>", conv.toString(out, null));
    }
    public void testToStreamSourceByStAXSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        StAXSource source = conv.toStAXSource("<foo>bar</foo>", null);
        StreamSource out = conv.toStreamSource(source, null);
        assertNotSame(source, out);

        assertEquals("<foo>bar</foo>", conv.toString(out, null));
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

        StreamSource out = conv.toStreamSource(dummy, null);
        assertNull(out);
    }

    public void testToStreamSourceByInputStream() throws Exception {
        XmlConverter conv = new XmlConverter();

        InputStream is = context.getTypeConverter().convertTo(InputStream.class, "<foo>bar</foo>");
        StreamSource out = conv.toStreamSource(is);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", conv.toString(out, null));
    }

    public void testToStreamSourceByReader() throws Exception {
        XmlConverter conv = new XmlConverter();

        Reader reader = context.getTypeConverter().convertTo(Reader.class, "<foo>bar</foo>");
        StreamSource out = conv.toStreamSource(reader);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", conv.toString(out, null));
    }

    public void testToStreamSourceByByteArray() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        XmlConverter conv = new XmlConverter();

        byte[] bytes = context.getTypeConverter().convertTo(byte[].class, "<foo>bar</foo>");
        StreamSource out = conv.toStreamSource(bytes, exchange);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", conv.toString(out, null));
    }

    public void testToStreamSourceByByteBuffer() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        XmlConverter conv = new XmlConverter();

        ByteBuffer bytes = context.getTypeConverter().convertTo(ByteBuffer.class, "<foo>bar</foo>");
        StreamSource out = conv.toStreamSource(bytes, exchange);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", conv.toString(out, null));
    }

    public void testToReaderFromSource() throws Exception {
        XmlConverter conv = new XmlConverter();
        SAXSource source = conv.toSAXSource("<foo>bar</foo>", null);
        
        Reader out = conv.toReaderFromSource(source, null);
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

    public void testToDomSourceFromFile() throws Exception {
        XmlConverter conv = new XmlConverter();

        template.sendBodyAndHeader("file:target/xml", "<foo>bar</foo>", Exchange.FILE_NAME, "myxml.xml");
        File file = new File("target/xml/myxml.xml");

        DOMSource out = conv.toDOMSource(file);
        assertNotNull(out);
        assertEquals("<foo>bar</foo>", context.getTypeConverter().convertTo(String.class, out));
    }

    public void testToDomElement() throws Exception {
        XmlConverter conv = new XmlConverter();
        SAXSource source = conv.toSAXSource("<foo>bar</foo>", null);

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

        InputStream is = conv.toInputStream(doc, null);
        assertNotNull(is);
        assertEquals("<foo>bar</foo>", context.getTypeConverter().convertTo(String.class, is));
    }

    public void testToInputStreamNonAsciiFromDocument() throws Exception {
        XmlConverter conv = new XmlConverter();
        Document doc = context.getTypeConverter().convertTo(Document.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>\u99f1\u99ddb\u00e4r</foo>");

        InputStream is = conv.toInputStream(doc, null);
        assertNotNull(is);
        assertEquals("<foo>\u99f1\u99ddb\u00e4r</foo>", context.getTypeConverter().convertTo(String.class, is));
    }

    public void testToDocumentFromFile() throws Exception {
        XmlConverter conv = new XmlConverter();
        File file = new File("src/test/resources/org/apache/camel/converter/stream/test.xml");

        Document out = conv.toDOMDocument(file);
        assertNotNull(out);
        String s = context.getTypeConverter().convertTo(String.class, out);
        assertTrue(s.contains("<firstName>James</firstName>"));
    }

    public void testToInputStreamByDomSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        DOMSource source = conv.toDOMSource("<foo>bar</foo>");
        InputStream out = conv.toInputStream(source, null);
        assertNotSame(source, out);

        String s = context.getTypeConverter().convertTo(String.class, out);
        assertEquals("<foo>bar</foo>", s);
    }

    public void testToInputStreamNonAsciiByDomSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        DOMSource source = conv.toDOMSource("<foo>\u99f1\u99ddb\u00e4r</foo>");
        InputStream out = conv.toInputStream(source, null);
        assertNotSame(source, out);

        String s = context.getTypeConverter().convertTo(String.class, out);
        assertEquals("<foo>\u99f1\u99ddb\u00e4r</foo>", s);
    }

    public void testToInputSource() throws Exception {
        XmlConverter conv = new XmlConverter();

        InputStream is = context.getTypeConverter().convertTo(InputStream.class, "<foo>bar</foo>");
        InputSource out = conv.toInputSource(is, null);
        assertNotNull(out);
        assertNotNull(out.getByteStream());
    }
    
    public void testToInputSourceFromFile() throws Exception {
        XmlConverter conv = new XmlConverter();
        File file = new File("src/test/resources/org/apache/camel/converter/stream/test.xml");

        InputSource out = conv.toInputSource(file, null);
        assertNotNull(out);
        assertNotNull(out.getByteStream());
    }

    public void testOutOptionsFromCamelContext() throws Exception {
        CamelContext context = new DefaultCamelContext();        
        Exchange exchange =  new DefaultExchange(context);
        // shows how to set the OutputOptions from camelContext
        context.getGlobalOptions().put(XmlConverter.OUTPUT_PROPERTIES_PREFIX + OutputKeys.ENCODING, "UTF-8");
        context.getGlobalOptions().put(XmlConverter.OUTPUT_PROPERTIES_PREFIX + OutputKeys.STANDALONE, "no");
        XmlConverter conv = new XmlConverter();

        SAXSource source = conv.toSAXSource("<foo>bar</foo>", exchange);
        DOMSource out = conv.toDOMSource(source, exchange);
        assertNotSame(source, out);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><foo>bar</foo>", conv.toString(out, exchange));
    }

    public void testNodeListToNode() throws Exception {
        Document document = context.getTypeConverter().convertTo(Document.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<foo><hello>Hello World</hello></foo>");

        NodeList nl = document.getElementsByTagName("hello");
        assertEquals(1, nl.getLength());

        Node node = context.getTypeConverter().convertTo(Node.class, nl);
        assertNotNull(node);

        document = context.getTypeConverter().convertTo(Document.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<foo><hello>Hello World</hello><hello>Hello Camel</hello></foo>");

        nl = document.getElementsByTagName("hello");
        assertEquals(2, nl.getLength());

        // not possible as we have 2 elements in the node list
        node = context.getTypeConverter().convertTo(Node.class, nl);
        assertNull(node);

        // and we can convert with 1 again
        document = context.getTypeConverter().convertTo(Document.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<foo><hello>Hello World</hello></foo>");

        nl = document.getElementsByTagName("hello");
        assertEquals(1, nl.getLength());

        node = context.getTypeConverter().convertTo(Node.class, nl);
        assertNotNull(node);
    }

}
