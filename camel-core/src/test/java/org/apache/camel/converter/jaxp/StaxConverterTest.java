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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Arrays;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;

public class StaxConverterTest extends ContextTestSupport {

    private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final String TEST_XML = "<test>Test Message with umlaut \u00E4\u00F6\u00FC</test>"; // umlauts have different encoding in UTF-8 and ISO-8859-1 (Latin1)

    private static final String TEST_XML_WITH_XML_HEADER_ISO_8859_1 = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + TEST_XML;

    private static final ByteArrayInputStream TEST_XML_WITH_XML_HEADER_ISO_8859_1_AS_BYTE_ARRAY_STREAM = new ByteArrayInputStream(
            TEST_XML_WITH_XML_HEADER_ISO_8859_1.getBytes(ISO_8859_1));

    private static final String TEST_XML_WITH_XML_HEADER = "<?xml version=\"1.0\"?>" + TEST_XML;

    private static final String TEST_XML_7000;

    static {
        StringBuilder sb = new StringBuilder(7000);
        // using quote character to make the plain characters comparison work with the generated xml
        sb.append("<?xml version='1.0' encoding='utf-8'?>").append("<list>");
        int n = 6963 - TEST_XML.length();
        while (n > 0) {
            sb.append(TEST_XML);
            n -= TEST_XML.length();
        }
        sb.append("</list>");

        TEST_XML_7000 = sb.toString();
    }

    public void testEncodingXmlEventReader() throws Exception {
        TEST_XML_WITH_XML_HEADER_ISO_8859_1_AS_BYTE_ARRAY_STREAM.reset();
        XMLEventReader reader = null;
        XMLEventWriter writer = null;
        ByteArrayOutputStream output = null;
        try {
            // enter text encoded with Latin1
            reader = context.getTypeConverter().mandatoryConvertTo(XMLEventReader.class,
                    TEST_XML_WITH_XML_HEADER_ISO_8859_1_AS_BYTE_ARRAY_STREAM);

            output = new ByteArrayOutputStream();
            // ensure UTF-8 encoding
            Exchange exchange = new DefaultExchange(context);
            exchange.setProperty(Exchange.CHARSET_NAME, UTF_8.toString());
            writer = context.getTypeConverter().mandatoryConvertTo(XMLEventWriter.class, exchange, output);
            while (reader.hasNext()) {
                writer.add(reader.nextEvent());
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
        }
        assertNotNull(output);

        String result = new String(output.toByteArray(), UTF_8.name());
        // normalize the auotation mark
        if (result.indexOf('\'') > 0) {
            result = result.replace('\'', '"');
        }
        boolean equals = TEST_XML_WITH_XML_HEADER.equals(result) || TEST_XML_WITH_XML_HEADER_ISO_8859_1.equals(result);
        assertTrue("Should match header", equals);
    }

    public void testEncodingXmlStreamReader() throws Exception {
        TEST_XML_WITH_XML_HEADER_ISO_8859_1_AS_BYTE_ARRAY_STREAM.reset();

        XMLStreamReader reader = null;
        XMLStreamWriter writer = null;
        ByteArrayOutputStream output = null;
        try {
            // enter text encoded with Latin1
            reader = context.getTypeConverter().mandatoryConvertTo(XMLStreamReader.class,
                    TEST_XML_WITH_XML_HEADER_ISO_8859_1_AS_BYTE_ARRAY_STREAM);

            output = new ByteArrayOutputStream();
            // ensure UTF-8 encoding
            Exchange exchange = new DefaultExchange(context);
            exchange.setProperty(Exchange.CHARSET_NAME, UTF_8.name());
            writer = context.getTypeConverter().mandatoryConvertTo(XMLStreamWriter.class, exchange, output);
            // copy to writer
            while (reader.hasNext()) {
                reader.next();
                switch (reader.getEventType()) {
                case XMLStreamConstants.START_DOCUMENT:
                    writer.writeStartDocument();
                    break;
                case XMLStreamConstants.END_DOCUMENT:
                    writer.writeEndDocument();
                    break;
                case XMLStreamConstants.START_ELEMENT:
                    writer.writeStartElement(reader.getName().getLocalPart());
                    break;
                case XMLStreamConstants.CHARACTERS:
                    writer.writeCharacters(reader.getText());
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    writer.writeEndElement();
                    break;
                default:
                    break;
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
        }
        assertNotNull(output);

        String result = new String(output.toByteArray(), UTF_8.name());

        assertEquals(TEST_XML, result);
    }

    public void testToReaderByXmlStreamReader() throws Exception {
        StringReader src = new StringReader(TEST_XML_7000);
        XMLStreamReader xreader = null;
        Reader reader = null;
        try {
            xreader = context.getTypeConverter().mandatoryConvertTo(XMLStreamReader.class, src);
            reader = context.getTypeConverter().mandatoryConvertTo(Reader.class, xreader);

            // verify
            StringReader expected = new StringReader(TEST_XML_7000);
            char[] tmp1 = new char[512];
            char[] tmp2 = new char[512];
            for (;;) {
                int n1 = 0;
                int n2 = 0;
                try {
                    n1 = expected.read(tmp1, 0, tmp1.length);
                    n2 = reader.read(tmp2, 0, tmp2.length);
                } catch (IOException e) {
                    fail("unable to read data");
                }
                assertEquals(n1, n2);
                if (n2 < 0) {
                    break;
                }
                assertTrue(Arrays.equals(tmp1,  tmp2));
            }
        } finally {
            if (xreader != null) {
                xreader.close();
            }
            if (reader != null) {
                reader.close();
            }
        }
    }
    public void testToInputSreamByXmlStreamReader() throws Exception {
        StringReader src = new StringReader(TEST_XML_7000);
        XMLStreamReader xreader = null;
        InputStream in = null;
        try {
            xreader = context.getTypeConverter().mandatoryConvertTo(XMLStreamReader.class, src);
            in = context.getTypeConverter().mandatoryConvertTo(InputStream.class, xreader);

            // verify
            InputStream expected = new ByteArrayInputStream(TEST_XML_7000.getBytes("utf-8"));
            byte[] tmp1 = new byte[512];
            byte[] tmp2 = new byte[512];
            for (;;) {
                int n1 = 0;
                int n2 = 0;
                try {
                    n1 = expected.read(tmp1, 0, tmp1.length);
                    n2 = in.read(tmp2, 0, tmp2.length);
                } catch (IOException e) {
                    fail("unable to read data");
                }
                assertEquals(n1, n2);
                if (n2 < 0) {
                    break;
                }
                assertTrue(Arrays.equals(tmp1,  tmp2));
            }
        } finally {
            if (xreader != null) {
                xreader.close();
            }
            if (in != null) {
                in.close();
            }
        }
    }
}
