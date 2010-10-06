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
import java.nio.charset.UnsupportedCharsetException;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
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
    public static InputStream toInputStream(File file) throws IOException {
        return new BufferedInputStream(new FileInputStream(file));
    }

    @Deprecated
    public static BufferedReader toReader(File file) throws IOException {
        return toReader(file, null);
    }

    @Converter
    public static BufferedReader toReader(File file, Exchange exchange) throws IOException {
        return new BufferedReader(new EncodingFileReader(file, getCharsetName(exchange)));
    }

    @Converter
    public static File toFile(String name) throws FileNotFoundException {
        return new File(name);
    }

    @Converter
    public static OutputStream toOutputStream(File file) throws FileNotFoundException {
        return new BufferedOutputStream(new FileOutputStream(file));
    }

    @Deprecated
    public static BufferedWriter toWriter(File file) throws IOException {
        return toWriter(file, null);
    }
    
    @Converter
    public static BufferedWriter toWriter(File file, Exchange exchange) throws IOException {
        return new BufferedWriter(new EncodingFileWriter(file, getCharsetName(exchange)));
    }

    @Deprecated
    public static Reader toReader(InputStream in) throws IOException {
        return toReader(in, null);
    }
        
    @Converter
    public static Reader toReader(InputStream in, Exchange exchange) throws IOException {
        return new InputStreamReader(in, getCharsetName(exchange));
    }

    @Deprecated
    public static Writer toWriter(OutputStream out) throws IOException {
        return toWriter(out, null);
    }
    
    @Converter
    @Deprecated
    public static Writer toWriter(OutputStream out, Exchange exchange) throws IOException {
        return new OutputStreamWriter(out, getCharsetName(exchange));
    }

    @Converter
    public static StringReader toReader(String text) {
        return new StringReader(text);
    }

    @Deprecated
    public static InputStream toInputStream(String text) throws IOException {
        return toInputStream(text, null);
    }
    
    @Converter
    public static InputStream toInputStream(String text, Exchange exchange) throws IOException {
        return toInputStream(text.getBytes(getCharsetName(exchange)));
    }
    
    @Deprecated
    public static InputStream toInputStream(BufferedReader buffer) throws IOException {
        return toInputStream(buffer, null);
    }
    
    @Converter
    public static InputStream toInputStream(BufferedReader buffer, Exchange exchange) throws IOException {
        return toInputStream(toString(buffer), exchange);
    }

    @Deprecated
    public static String toString(byte[] data) throws IOException {
        return toString(data, null);
    }
    
    @Converter
    public static String toString(byte[] data, Exchange exchange) throws IOException {
        return new String(data, getCharsetName(exchange));
    }

    @Deprecated
    public static String toString(File file) throws IOException {
        return toString(file, null);
    }
    
    @Converter
    public static String toString(File file, Exchange exchange) throws IOException {
        return toString(toReader(file, exchange));
    }

    @Converter
    public static byte[] toByteArray(File file) throws IOException {
        InputStream is = toInputStream(file);
        try {
            return toBytes(is);
        } finally {
            IOHelper.close(is, "file", LOG);
        }
    }
    
    @Deprecated
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

    @Deprecated
    public static String toString(URL url) throws IOException {
        return toString(url, null);
    }

    @Converter
    public static String toString(URL url, Exchange exchange) throws IOException {
        InputStream is = toInputStream(url);
        try {
            return toString(is, exchange);
        } finally {
            IOHelper.close(is, "url", LOG);
        }
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
            IOHelper.close(reader, "reader", LOG);
        }

        return sb.toString();
    }
    
    @Deprecated
    public static byte[] toByteArray(BufferedReader reader) throws IOException {
        return toByteArray(reader, null);
    }
    
    @Converter
    public static byte[] toByteArray(BufferedReader reader, Exchange exchange) throws IOException {
        return toByteArray(toString(reader), exchange);
    }

    @Deprecated
    public static byte[] toByteArray(String value) throws IOException {
        return toByteArray(value, null);
    }

    @Converter
    public static byte[] toByteArray(String value, Exchange exchange) throws IOException {
        return value != null ? value.getBytes(getCharsetName(exchange)) : null;
    }

    @Deprecated
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
            IOHelper.close(bos, "stream", LOG);
        }
    }

    @Converter
    public static byte[] toByteArray(ByteArrayOutputStream os) {
        return os.toByteArray();
    }

    @Deprecated
    public static String toString(ByteArrayOutputStream os) throws IOException {
        return toString(os, null);
    }

    @Converter
    public static String toString(ByteArrayOutputStream os, Exchange exchange) throws IOException {
        return os.toString(getCharsetName(exchange));
    }

    @Converter
    public static InputStream toInputStream(ByteArrayOutputStream os) {
        return new ByteArrayInputStream(os.toByteArray());
    }

    public static String getCharsetName(Exchange exchange) {
        return getCharsetName(exchange, true);
    }

    /**
     * Gets the charset name if set as property {@link Exchange#CHARSET_NAME}.
     *
     * @param exchange  the exchange
     * @param useDefault should we fallback and use JVM default charset if no property existed?
     * @return the charset, or <tt>null</tt> if no found
     */
    public static String getCharsetName(Exchange exchange, boolean useDefault) {
        if (exchange != null) {
            String charsetName = exchange.getProperty(Exchange.CHARSET_NAME, String.class);
            if (charsetName != null) {
                return IOConverter.normalizeCharset(charsetName);
            }
        }
        if (useDefault) {
            return getDefaultCharsetName();
        } else {
            return null;
        }
    }
    
    public static String getDefaultCharsetName() {
        return ObjectHelper.getSystemProperty(Exchange.DEFAULT_CHARSET_PROPERTY, "UTF-8");
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

    /**
     * This method will take off the quotes and double quotes of the charset
     */
    public static String normalizeCharset(String charset) {
        if (charset != null) {
            String answer = charset.trim();
            if (answer.startsWith("'") || answer.startsWith("\"")) {
                answer = answer.substring(1);
            }
            if (answer.endsWith("'") || answer.endsWith("\"")) {
                answer = answer.substring(0, answer.length() - 1);
            }
            return answer.trim();
        } else {
            return null;
        }
    }
    
    public static void validateCharset(String charset) throws UnsupportedCharsetException {
        if (charset != null) {
            if (Charset.isSupported(charset)) {
                Charset.forName(charset);
                return;
            }
        }
        throw new UnsupportedCharsetException(charset);
    }
    
}
