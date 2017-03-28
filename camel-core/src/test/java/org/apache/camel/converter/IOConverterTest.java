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
package org.apache.camel.converter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.URL;
import java.util.Properties;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Test case for {@link IOConverter}
 */
public class IOConverterTest extends ContextTestSupport {

    private static final byte[] TESTDATA = "My test data".getBytes();

    public void testToBytes() throws Exception {
        File file = new File("src/test/resources/org/apache/camel/converter/dummy.txt");
        byte[] data = IOConverter.toBytes(new FileInputStream(file));
        assertEquals("get the wrong byte size", file.length(), data.length);
        assertEquals('#', (char) data[0]);

        // should contain Hello World!
        String s = new String(data);
        assertTrue("Should contain Hello World!", s.contains("Hello World"));
    }

    public void testCopy() throws Exception {
        ByteArrayInputStream bis = new ByteArrayInputStream(TESTDATA);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOHelper.copy(bis, bos);
        assertEquals(TESTDATA, bos.toByteArray());
    }

    private void assertEquals(byte[] data1, byte[] data2) {
        assertEquals(data1.length, data2.length);
        for (int i = 0; i < data1.length; i++) {
            assertEquals(data1[i], data2[i]);
        }
    }

    public void testToOutputStreamFile() throws Exception {
        template.sendBodyAndHeader("file://target/test", "Hello World", Exchange.FILE_NAME, "hello.txt");
        File file = new File("target/test/hello.txt");

        OutputStream os = IOConverter.toOutputStream(file);
        assertIsInstanceOf(BufferedOutputStream.class, os); 
        os.close();
    }

    public void testToWriterFile() throws Exception {
        template.sendBodyAndHeader("file://target/test", "Hello World", Exchange.FILE_NAME, "hello.txt");
        File file = new File("target/test/hello.txt");

        Writer writer = IOConverter.toWriter(file, null);
        assertIsInstanceOf(BufferedWriter.class, writer);
        writer.close();
    }

    public void testToReader() throws Exception {
        StringReader reader = IOConverter.toReader("Hello");
        assertEquals("Hello", IOConverter.toString(reader));
    }

    public void testToInputStreamExchange() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(Exchange.CHARSET_NAME, ObjectHelper.getDefaultCharacterSet());

        InputStream is = IOConverter.toInputStream("Hello World", exchange);
        assertNotNull(is);
        assertEquals("Hello World", IOConverter.toString(is, exchange));
    }
    
    public void testToInputStreamStringBufferAndBuilderExchange() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(Exchange.CHARSET_NAME, ObjectHelper.getDefaultCharacterSet());
        StringBuffer buffer = new StringBuffer();
        buffer.append("Hello World");
        InputStream is = IOConverter.toInputStream(buffer, exchange);
        assertNotNull(is);
        assertEquals("Hello World", IOConverter.toString(is, exchange));
        
        StringBuilder builder = new StringBuilder();
        builder.append("Hello World");
        is = IOConverter.toInputStream(builder, exchange);
        assertNotNull(is);
        assertEquals("Hello World", IOConverter.toString(is, exchange));
    }

    public void testToInputStreamBufferReader() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(Exchange.CHARSET_NAME, ObjectHelper.getDefaultCharacterSet());

        BufferedReader br = IOHelper.buffered(new StringReader("Hello World"));
        InputStream is = IOConverter.toInputStream(br, exchange);
        assertNotNull(is);
    }

    public void testToByteArrayFile() throws Exception {
        template.sendBodyAndHeader("file://target/test", "Hello World", Exchange.FILE_NAME, "hello.txt");
        File file = new File("target/test/hello.txt");

        byte[] data = IOConverter.toByteArray(file);
        assertNotNull(data);
        assertEquals(11, data.length);
    }

    public void testToStringBufferReader() throws Exception {
        BufferedReader br = IOHelper.buffered(new StringReader("Hello World"));
        String s = IOConverter.toString(br);
        assertNotNull(s);
        assertEquals("Hello World", s);
    }

    public void testToByteArrayBufferReader() throws Exception {
        BufferedReader br = IOHelper.buffered(new StringReader("Hello World"));
        byte[] bytes = IOConverter.toByteArray(br, null);
        assertNotNull(bytes);
        assertEquals(11, bytes.length);
    }

    public void testToByteArrayReader() throws Exception {
        Reader br = IOHelper.buffered(new StringReader("Hello World"));
        byte[] bytes = IOConverter.toByteArray(br, null);
        assertNotNull(bytes);
        assertEquals(11, bytes.length);
    }

    public void testToByteArrayOutputStream() throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write("Hello World".getBytes());
        byte[] bytes = IOConverter.toByteArray(os);
        assertNotNull(bytes);
        assertEquals(11, bytes.length);
    }

    public void testToStringOutputStream() throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write("Hello World".getBytes());
        String s = IOConverter.toString(os, null);
        assertNotNull(s);
        assertEquals("Hello World", s);
    }

    public void testToInputStreamOutputStream() throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write("Hello World".getBytes());

        InputStream is = IOConverter.toInputStream(os);
        assertNotNull(is);
        assertEquals("Hello World", IOConverter.toString(is, null));
    }

    public void testToInputStreamUrl() throws Exception {
        URL url = ObjectHelper.loadResourceAsURL("log4j2.properties");
        InputStream is = IOConverter.toInputStream(url);
        assertIsInstanceOf(BufferedInputStream.class, is);
    }

    public void testStringUrl() throws Exception {
        URL url = ObjectHelper.loadResourceAsURL("log4j2.properties");
        String s = IOConverter.toString(url, null);
        assertNotNull(s);
    }

    public void testStringByBufferedReader() throws Exception {
        BufferedReader br = IOHelper.buffered(new StringReader("Hello World"));
        assertEquals("Hello World", IOConverter.toString(br));
    }

    public void testByteArrayByBufferedReader() throws Exception {
        Reader reader = new StringReader("Hello World");
        byte[] data = IOConverter.toByteArray(reader, null);
        assertNotNull(data);
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, data));
    }
    
    public void testInputStreamToString() throws Exception {
        String data = "46\u00B037'00\"N\"";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes("UTF-8"));
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
        String result = IOConverter.toString(is, exchange);
        assertEquals("Get a wrong result", data, result);
    }

    public void testToPropertiesFromReader() throws Exception {
        Reader br = IOHelper.buffered(new StringReader("foo=123\nbar=456"));
        Properties p = IOConverter.toProperties(br);
        assertNotNull(p);
        assertEquals(2, p.size());
        assertEquals("123", p.get("foo"));
        assertEquals("456", p.get("bar"));
    }

    public void testToPropertiesFromFile() throws Exception {
        Properties p = IOConverter.toProperties(new File("src/test/resources/log4j2.properties"));
        assertNotNull(p);
        assertTrue("Should be 8 or more properties, was " + p.size(), p.size() >= 8);
        String root = (String) p.get("rootLogger.level");
        assertNotNull(root);
        assertTrue(root.contains("INFO"));
    }

}
