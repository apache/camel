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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.Charset;

import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Some core java.io based <a
 * href="http://camel.apache.org/type-converter.html">Type Converters</a>
 *
 * @version $Revision$
 */
@Converter
public final class IOConverter {
    private static final transient Log LOG = LogFactory.getLog(IOConverter.class);
    private static XmlConverter xmlConverter;

    /**
     * Utility classes should not have a public constructor.
     */
    private IOConverter() {
    }

    @Converter
    public static InputStream toInputStream(URL url) throws IOException {
        return url.openStream();
    }

    @Converter
    public static InputStream toInputStream(File file) throws FileNotFoundException {
        return new BufferedInputStream(new FileInputStream(file));
    }

    public static BufferedReader toReader(File file) throws FileNotFoundException {
        return toReader(file, null);
    }

    @Converter
    public static BufferedReader toReader(File file, Exchange exchange) throws FileNotFoundException {
        try {
            return new BufferedReader(new EncodingFileReader(file, getCharsetName(exchange)));
        } catch (UnsupportedEncodingException e) {
            LOG.warn("Cannot convert File into BufferedReader with charset: " + getCharsetName(exchange), e);
            return new BufferedReader(new FileReader(file));
        }
    }

    @Converter
    public static File toFile(String name) throws FileNotFoundException {
        return new File(name);
    }

    @Converter
    public static OutputStream toOutputStream(File file) throws FileNotFoundException {
        return new BufferedOutputStream(new FileOutputStream(file));
    }

    public static BufferedWriter toWriter(File file) throws IOException {
        return toWriter(file, null);
    }
    
    @Converter
    public static BufferedWriter toWriter(File file, Exchange exchange) throws IOException {
        try {   
            return new BufferedWriter(new EncodingFileWriter(file, getCharsetName(exchange)));
        } catch (UnsupportedEncodingException e) {
            LOG.warn("Cannot convert File into BufferedWriter with charset: " + getCharsetName(exchange), e);
            return new BufferedWriter(new FileWriter(file));
        }
    }

    public static Reader toReader(InputStream in) {
        return toReader(in, null);
    }
        
    @Converter
    public static Reader toReader(InputStream in, Exchange exchange) {
        try {
            return new InputStreamReader(in, getCharsetName(exchange));
        } catch (UnsupportedEncodingException e) {
            LOG.warn("Cannot convert InputStream into InputStreamReader with charset: " + getCharsetName(exchange), e);
            return new InputStreamReader(in);
        }
    }

    public static Writer toWriter(OutputStream out) {
        return toWriter(out, null);
    }
    
    @Converter
    public static Writer toWriter(OutputStream out, Exchange exchange) {
        try {
            return new OutputStreamWriter(out, getCharsetName(exchange));
        } catch (UnsupportedEncodingException e) {
            LOG.warn("Cannot convert OutputStream into OutputStreamWriter with charset: " + getCharsetName(exchange), e);
            return new OutputStreamWriter(out);
        }
    }

    @Converter
    public static StringReader toReader(String text) {
        return new StringReader(text);
    }

    public static InputStream toInputStream(String text) {
        return toInputStream(text, null);
    }
    
    @Converter
    public static InputStream toInputStream(String text, Exchange exchange) {
        try {
            return toInputStream(text.getBytes(getCharsetName(exchange)));
        } catch (UnsupportedEncodingException e) {
            LOG.warn("Cannot convert String into InputStream with charset: " + getCharsetName(exchange), e);
            return toInputStream(text.getBytes());
        }
    }
    
    public static InputStream toInputStream(BufferedReader buffer) throws IOException {
        return toInputStream(buffer, null);
    }
    
    @Converter
    public static InputStream toInputStream(BufferedReader buffer, Exchange exchange) throws IOException {
        return toInputStream(toString(buffer), exchange);
    }

    @Converter
    public static InputStream toInputStrean(DOMSource source) throws TransformerException, IOException {
        XmlConverter xmlConverter = createXmlConverter();
        return new ByteArrayInputStream(xmlConverter.toString(source).getBytes());
    }

    private static synchronized XmlConverter createXmlConverter() {
        if (xmlConverter == null) {
            xmlConverter = new XmlConverter();
        }
        return xmlConverter;
    }

    public static String toString(byte[] data) {
        return toString(data, null);
    }
    
    @Converter
    public static String toString(byte[] data, Exchange exchange) {
        try {
            return new String(data, getCharsetName(exchange));
        } catch (UnsupportedEncodingException e) {
            LOG.warn("Cannot convert byte[] into String with charset: " + getCharsetName(exchange), e);
            return new String(data);
        }
    }


    public static String toString(File file) throws IOException {
        return toString(file, null);
    }
    
    @Converter
    public static String toString(File file, Exchange exchange) throws IOException {
        return toString(toReader(file, exchange));
    }

    @Converter
    public static byte[] toByteArray(File file) throws IOException {
        return toBytes(toInputStream(file));
    }
    
    public static byte[] toByteArray(Reader reader) throws IOException {
        return toByteArray(reader, null);
    }
    
    @Converter
    public static byte[] toByteArray(Reader reader, Exchange exchange) throws IOException {
        if (reader instanceof BufferedReader) {
            return toByteArray((BufferedReader)reader, exchange);
        } else {
            return toByteArray(new BufferedReader(reader), exchange);
        }
    }

    public static String toString(URL url) throws IOException {
        return toString(url, null);
    }

    @Converter
    public static String toString(URL url, Exchange exchange) throws IOException {
        return toString(toInputStream(url), exchange);
    }

    @Converter
    public static String toString(Reader reader) throws IOException {
        if (reader instanceof BufferedReader) {
            return toString((BufferedReader)reader);
        } else {
            return toString(new BufferedReader(reader));
        }
    }

    @Converter
    public static String toString(BufferedReader reader) throws IOException {
        if (reader == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(1024);
        char[] buf = new char[1024];
        try {
            int len = 0;
            // read until we reach then end which is the -1 marker
            while (len != -1) {
                len = reader.read(buf);
                if (len != -1) {
                    sb.append(buf, 0, len);
                }
            }
        } finally {
            ObjectHelper.close(reader, "reader", LOG);
        }

        return sb.toString();
    }
    
    public static byte[] toByteArray(BufferedReader reader) throws IOException {
        return toByteArray(reader, null);
    }
    
    @Converter
    public static byte[] toByteArray(BufferedReader reader, Exchange exchange) throws IOException {
        return toByteArray(toString(reader), exchange);
    }

    public static byte[] toByteArray(String value) {
        return toByteArray(value, null);
    }

    @Converter
    public static byte[] toByteArray(String value, Exchange exchange) {
        try {
            return value != null ? value.getBytes(getCharsetName(exchange)) : null;
        } catch (UnsupportedEncodingException e) {
            LOG.warn("Cannot convert String into byte[] with charset: " + getCharsetName(exchange), e);
            return value != null ? value.getBytes() : null;
        }
    }

    public static String toString(InputStream in) throws IOException {
        return toString(in, null);
    }

    @Converter
    public static String toString(InputStream in, Exchange exchange) throws IOException {
        return toString(toReader(in, exchange));
    }

    @Converter
    public static InputStream toInputStream(byte[] data) {
        return new ByteArrayInputStream(data);
    }

    @Converter
    public static ObjectOutput toObjectOutput(OutputStream stream) throws IOException {
        if (stream instanceof ObjectOutput) {
            return (ObjectOutput) stream;
        } else {
            return new ObjectOutputStream(stream);
        }
    }

    @Converter
    public static ObjectInput toObjectInput(InputStream stream) throws IOException {
        if (stream instanceof ObjectInput) {
            return (ObjectInput) stream;
        } else {
            return new ObjectInputStream(stream);
        }
    }

    @Converter
    public static byte[] toBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            IOHelper.copy(stream, bos);
            return bos.toByteArray();
        } finally {
            ObjectHelper.close(bos, "stream", LOG);
        }
    }

    @Converter
    public static byte[] toByteArray(ByteArrayOutputStream os) {
        return os.toByteArray();
    }

    public static String toString(ByteArrayOutputStream os) {
        return toString(os, null);
    }

    @Converter
    public static String toString(ByteArrayOutputStream os, Exchange exchange) {
        try {
            return os.toString(getCharsetName(exchange));
        } catch (UnsupportedEncodingException e) {
            LOG.warn("Cannot convert ByteArrayOutputStream into String with charset: " + getCharsetName(exchange), e);
            return os.toString();
        }
    }

    @Converter
    public static InputStream toInputStream(ByteArrayOutputStream os) {
        return new ByteArrayInputStream(os.toByteArray());
    }

    private static String getCharsetName(Exchange exchange) {
        if (exchange != null) {
            String charsetName = exchange.getProperty(Exchange.CHARSET_NAME, String.class);
            if (charsetName != null) {
                return charsetName;
            }
        }
        return getDefaultCharsetName();
    }
    
    private static String getDefaultCharsetName() {
        return Charset.defaultCharset().toString();
    }
    
    /**
     * Encoding-aware file reader. 
     */
    private static class EncodingFileReader extends InputStreamReader {

        /**
         * @param file file to read
         * @param charset character set to use
         */
        public EncodingFileReader(File file, String charset)
            throws FileNotFoundException, UnsupportedEncodingException {
            super(new FileInputStream(file), charset);
        }

    }
    
    /**
     * Encoding-aware file writer. 
     */
    private static class EncodingFileWriter extends OutputStreamWriter {

        /**
         * @param file file to write
         * @param charset character set to use
         */
        public EncodingFileWriter(File file, String charset)
            throws FileNotFoundException, UnsupportedEncodingException {
            super(new FileOutputStream(file), charset);
        }

    }
    
}
