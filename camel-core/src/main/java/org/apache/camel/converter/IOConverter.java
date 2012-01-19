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
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.UnsupportedCharsetException;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Some core java.io based <a
 * href="http://camel.apache.org/type-converter.html">Type Converters</a>
 *
 * @version 
 */
@Converter
public final class IOConverter {
    private static final transient Logger LOG = LoggerFactory.getLogger(IOConverter.class);

    /**
     * Utility classes should not have a public constructor.
     */
    private IOConverter() {
    }

    @Converter
    public static InputStream toInputStream(URL url) throws IOException {
        return IOHelper.buffered(url.openStream());
    }

    @Converter
    public static InputStream toInputStream(File file) throws IOException {
        return IOHelper.buffered(new FileInputStream(file));
    }

    /**
     * @deprecated will be removed in Camel 3.0. Use the method which has 2 parameters.
     */
    @Deprecated
    public static BufferedReader toReader(File file) throws IOException {
        return toReader(file, null);
    }

    @Converter
    public static BufferedReader toReader(File file, Exchange exchange) throws IOException {
        return IOHelper.buffered(new EncodingFileReader(file, IOHelper.getCharsetName(exchange)));
    }

    @Converter
    public static File toFile(String name) throws FileNotFoundException {
        return new File(name);
    }

    @Converter
    public static OutputStream toOutputStream(File file) throws FileNotFoundException {
        return IOHelper.buffered(new FileOutputStream(file));
    }

    /**
     * @deprecated will be removed in Camel 3.0. Use the method which has 2 parameters.
     */
    @Deprecated
    public static BufferedWriter toWriter(File file) throws IOException {
        return toWriter(file, null);
    }
    
    @Converter
    public static BufferedWriter toWriter(File file, Exchange exchange) throws IOException {
        return IOHelper.buffered(new EncodingFileWriter(file, IOHelper.getCharsetName(exchange)));
    }

    /**
     * @deprecated will be removed in Camel 3.0. Use the method which has 2 parameters.
     */
    @Deprecated
    public static Reader toReader(InputStream in) throws IOException {
        return toReader(in, null);
    }

    @Converter
    public static Reader toReader(InputStream in, Exchange exchange) throws IOException {
        return IOHelper.buffered(new InputStreamReader(in, IOHelper.getCharsetName(exchange)));
    }

    /**
     * @deprecated will be removed in Camel 3.0. Use the method which has 2 parameters.
     */
    @Deprecated
    public static Writer toWriter(OutputStream out) throws IOException {
        return toWriter(out, null);
    }
    
    @Converter
    public static Writer toWriter(OutputStream out, Exchange exchange) throws IOException {
        return IOHelper.buffered(new OutputStreamWriter(out, IOHelper.getCharsetName(exchange)));
    }

    @Converter
    public static StringReader toReader(String text) {
        // no buffering required as the complete string input is already passed
        // over as a whole
        return new StringReader(text);
    }

    /**
     * @deprecated will be removed in Camel 3.0. Use the method which has 2 parameters.
     */
    @Deprecated
    public static InputStream toInputStream(String text) throws IOException {
        return toInputStream(text, null);
    }
    
    @Converter
    public static InputStream toInputStream(String text, Exchange exchange) throws IOException {
        return toInputStream(text.getBytes(IOHelper.getCharsetName(exchange)));
    }
    
    @Converter
    public static InputStream toInputStream(StringBuffer buffer, Exchange exchange) throws IOException {
        return toInputStream(buffer.toString(), exchange);
    }
    
    @Converter
    public static InputStream toInputStream(StringBuilder builder, Exchange exchange) throws IOException {
        return toInputStream(builder.toString(), exchange);
    }
    
    /**
     * @deprecated will be removed in Camel 3.0. Use the method which has 2 parameters.
     */
    @Deprecated
    public static InputStream toInputStream(BufferedReader buffer) throws IOException {
        return toInputStream(buffer, null);
    }
    
    @Converter
    public static InputStream toInputStream(BufferedReader buffer, Exchange exchange) throws IOException {
        return toInputStream(toString(buffer), exchange);
    }

    /**
     * @deprecated will be removed in Camel 3.0. Use the method which has 2 parameters.
     */
    @Deprecated
    public static String toString(byte[] data) throws IOException {
        return toString(data, null);
    }
    
    @Converter
    public static String toString(byte[] data, Exchange exchange) throws IOException {
        return new String(data, IOHelper.getCharsetName(exchange));
    }

    /**
     * @deprecated will be removed in Camel 3.0. Use the method which has 2 parameters.
     */
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
    
    /**
     * @deprecated will be removed in Camel 3.0. Use the method which has 2 parameters.
     */
    @Deprecated
    public static byte[] toByteArray(Reader reader) throws IOException {
        return toByteArray(reader, null);
    }
    
    @Converter
    public static byte[] toByteArray(Reader reader, Exchange exchange) throws IOException {
        return toByteArray(IOHelper.buffered(reader), exchange);
    }

    /**
     * @deprecated will be removed in Camel 3.0. Use the method which has 2 parameters.
     */
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
        return toString(IOHelper.buffered(reader));
    }

    @Converter
    public static String toString(BufferedReader reader) throws IOException {
        if (reader == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(1024);
        char[] buf = new char[1024];
        try {
            int len;
            // read until we reach then end which is the -1 marker
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
        } finally {
            IOHelper.close(reader, "reader", LOG);
        }

        return sb.toString();
    }
    
    /**
     * @deprecated will be removed in Camel 3.0. Use the method which has 2 parameters.
     */
    @Deprecated
    public static byte[] toByteArray(BufferedReader reader) throws IOException {
        return toByteArray(reader, null);
    }
    
    @Converter
    public static byte[] toByteArray(BufferedReader reader, Exchange exchange) throws IOException {
        return toByteArray(toString(reader), exchange);
    }

    /**
     * @deprecated will be removed in Camel 3.0. Use the method which has 2 parameters.
     */
    @Deprecated
    public static byte[] toByteArray(String value) throws IOException {
        return toByteArray(value, null);
    }

    @Converter
    public static byte[] toByteArray(String value, Exchange exchange) throws IOException {
        return value != null ? value.getBytes(IOHelper.getCharsetName(exchange)) : null;
    }

    /**
     * @deprecated will be removed in Camel 3.0. Use the method which has 2 parameters.
     */
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
        // no buffering required as the complete byte input is already passed
        // over as a whole
        return new ByteArrayInputStream(data);
    }

    @Converter
    public static ObjectOutput toObjectOutput(OutputStream stream) throws IOException {
        if (stream instanceof ObjectOutput) {
            return (ObjectOutput) stream;
        } else {
            return new ObjectOutputStream(IOHelper.buffered(stream));
        }
    }

    @Converter
    public static ObjectInput toObjectInput(final InputStream stream, final Exchange exchange) throws IOException {
        if (stream instanceof ObjectInput) {
            return (ObjectInput) stream;
        } else {
            return new ObjectInputStream(IOHelper.buffered(stream)) {
                @Override
                protected Class<?> resolveClass(ObjectStreamClass objectStreamClass) throws IOException, ClassNotFoundException {
                    // need to let Camel be able to resolve class using ClassResolver SPI, to let class loading
                    // work in OSGi and other containers
                    Class<?>  answer = null;
                    String name = objectStreamClass.getName();
                    if (exchange != null) {
                        LOG.trace("Loading class {} using Camel ClassResolver", name);
                        answer = exchange.getContext().getClassResolver().resolveClass(name);
                    }
                    if (answer == null) {
                        LOG.trace("Loading class {} using JDK default implementation", name);
                        answer = super.resolveClass(objectStreamClass);
                    }
                    return answer;
                }
            };
        }
    }

    @Converter
    public static byte[] toBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOHelper.copy(IOHelper.buffered(stream), bos);

        // no need to close the ByteArrayOutputStream as it's close()
        // implementation is noop
        return bos.toByteArray();
    }

    @Converter
    public static byte[] toByteArray(ByteArrayOutputStream os) {
        return os.toByteArray();
    }

    /**
     * @deprecated will be removed in Camel 3.0. Use the method which has 2 parameters.
     */
    @Deprecated
    public static String toString(ByteArrayOutputStream os) throws IOException {
        return toString(os, null);
    }

    @Converter
    public static String toString(ByteArrayOutputStream os, Exchange exchange) throws IOException {
        return os.toString(IOHelper.getCharsetName(exchange));
    }

    @Converter
    public static InputStream toInputStream(ByteArrayOutputStream os) {
        // no buffering required as the complete byte array input is already
        // passed over as a whole
        return new ByteArrayInputStream(os.toByteArray());
    }

    /**
     * Gets the charset name if set as property {@link Exchange#CHARSET_NAME}.
     *
     * @param exchange  the exchange
     * @param useDefault should we fallback and use JVM default charset if no property existed?
     * @return the charset, or <tt>null</tt> if no found
     */
    @Deprecated
    public static String getCharsetName(Exchange exchange, boolean useDefault) {
        return IOHelper.getCharsetName(exchange, useDefault);
    }
    
    @Deprecated
    public static String getCharsetName(Exchange exchange) {
        return getCharsetName(exchange, true);
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
    @Deprecated
    public static String normalizeCharset(String charset) {
        return IOHelper.normalizeCharset(charset);
    }
    
    @Deprecated
    public static void validateCharset(String charset) throws UnsupportedCharsetException {
        IOHelper.validateCharset(charset);
    }
    
}
