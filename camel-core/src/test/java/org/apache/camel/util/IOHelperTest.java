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
package org.apache.camel.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

import junit.framework.TestCase;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;

/**
 * @version 
 */
public class IOHelperTest extends TestCase {

    public void testIOException() {
        IOException io = new IOException("Damn", new IllegalArgumentException("Damn"));
        assertEquals("Damn", io.getMessage());
        assertTrue(io.getCause() instanceof IllegalArgumentException);
    }

    public void testIOExceptionWithMessage() {
        IOException io = new IOException("Not again", new IllegalArgumentException("Damn"));
        assertEquals("Not again", io.getMessage());
        assertTrue(io.getCause() instanceof IllegalArgumentException);
    }

    public void testNewStringFromBytes() {
        String s = IOHelper.newStringFromBytes("Hello".getBytes());
        assertEquals("Hello", s);
    }

    public void testNewStringFromBytesWithStart() {
        String s = IOHelper.newStringFromBytes("Hello".getBytes(), 2, 3);
        assertEquals("llo", s);
    }

    public void testCopyAndCloseInput() throws Exception {
        InputStream is = new ByteArrayInputStream("Hello".getBytes());
        OutputStream os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os, 256);
    }
    
    public void testCharsetNormalize() throws Exception {
        assertEquals("UTF-8", IOHelper.normalizeCharset("'UTF-8'"));
        assertEquals("UTF-8", IOHelper.normalizeCharset("\"UTF-8\""));
        assertEquals("UTF-8", IOHelper.normalizeCharset("\"UTF-8 \""));
        assertEquals("UTF-8", IOHelper.normalizeCharset("\' UTF-8\'"));
    }

    public void testLine1() throws Exception {
        assertReadAsWritten("line1",   "line1", "line1\n");
    }

    public void testLine1LF() throws Exception {
        assertReadAsWritten("line1LF", "line1\n", "line1\n");
    }

    public void testLine2() throws Exception {
        assertReadAsWritten("line2",   "line1\nline2", "line1\nline2\n");
    }

    public void testLine2LF() throws Exception {
        assertReadAsWritten("line2LF", "line1\nline2\n", "line1\nline2\n");
    }

    private void assertReadAsWritten(String testname, String text, String compareText) throws Exception {
        File file = tempFile(testname);
        write(file, text);
        String loadText = IOHelper.loadText(Files.newInputStream(Paths.get(file.getAbsolutePath())));
        assertEquals(compareText, loadText);
    }

    private File tempFile(String testname) throws Exception {
        return File.createTempFile(testname, "");
    }
 
    private void write(File file, String text) throws Exception {
        PrintWriter out = new PrintWriter(file);
        out.print(text);
        out.close();
    }

    public void testCharsetName() throws Exception {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());

        assertNull(IOHelper.getCharsetName(exchange, false));

        exchange.getIn().setHeader(Exchange.CHARSET_NAME, "iso-8859-1");
        assertEquals("iso-8859-1", IOHelper.getCharsetName(exchange, false));

        exchange.getIn().removeHeader(Exchange.CHARSET_NAME);
        exchange.setProperty(Exchange.CHARSET_NAME, "iso-8859-1");
        assertEquals("iso-8859-1", IOHelper.getCharsetName(exchange, false));
        
    }
    
    public void testGetCharsetNameFromContentType() throws Exception {
        String charsetName = IOHelper.getCharsetNameFromContentType("text/html; charset=iso-8859-1");
        assertEquals("iso-8859-1", charsetName);
        
        charsetName = IOHelper.getCharsetNameFromContentType("text/html");
        assertEquals("UTF-8", charsetName);
    }
}
