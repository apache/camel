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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IO helper class.
 *
 * @version 
 */
public final class IOHelper {
    
    private static final transient Logger LOG = LoggerFactory.getLogger(IOHelper.class);
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    private IOHelper() {
        //Utility Class
    }
    
    /**
     * Use this function instead of new String(byte[]) to avoid surprises from non-standard default encodings.
     */
    public static String newStringFromBytes(byte[] bytes) {
        try {
            return new String(bytes, UTF8_CHARSET.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Impossible failure: Charset.forName(\"utf-8\") returns invalid name.", e);
        }
    }

    /**
     * Use this function instead of new String(byte[], int, int) 
     * to avoid surprises from non-standard default encodings.
     */
    public static String newStringFromBytes(byte[] bytes, int start, int length) {
        try {
            return new String(bytes, start, length, UTF8_CHARSET.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Impossible failure: Charset.forName(\"utf-8\") returns invalid name.", e);
        }
    }

    /**
     * Wraps the passed <code>in</code> into a {@link BufferedInputStream}
     * object and returns that. If the passed <code>in</code> is already an
     * instance of {@link BufferedInputStream} returns the same passed
     * <code>in</code> reference as is (avoiding double wrapping).
     * 
     * @param in the wrapee to be used for the buffering support
     * @return the passed <code>in</code> decorated through a
     *         {@link BufferedInputStream} object as wrapper
     */
    public static BufferedInputStream buffered(InputStream in) {
        ObjectHelper.notNull(in, "in");
        return (in instanceof BufferedInputStream) ? (BufferedInputStream)in : new BufferedInputStream(in);
    }

    /**
     * Wraps the passed <code>out</code> into a {@link BufferedOutputStream}
     * object and returns that. If the passed <code>out</code> is already an
     * instance of {@link BufferedOutputStream} returns the same passed
     * <code>out</code> reference as is (avoiding double wrapping).
     * 
     * @param out the wrapee to be used for the buffering support
     * @return the passed <code>out</code> decorated through a
     *         {@link BufferedOutputStream} object as wrapper
     */
    public static BufferedOutputStream buffered(OutputStream out) {
        ObjectHelper.notNull(out, "out");
        return (out instanceof BufferedOutputStream) ? (BufferedOutputStream)out : new BufferedOutputStream(out);
    }

    /**
     * Wraps the passed <code>reader</code> into a {@link BufferedReader} object
     * and returns that. If the passed <code>reader</code> is already an
     * instance of {@link BufferedReader} returns the same passed
     * <code>reader</code> reference as is (avoiding double wrapping).
     * 
     * @param reader the wrapee to be used for the buffering support
     * @return the passed <code>reader</code> decorated through a
     *         {@link BufferedReader} object as wrapper
     */
    public static BufferedReader buffered(Reader reader) {
        ObjectHelper.notNull(reader, "reader");
        return (reader instanceof BufferedReader) ? (BufferedReader)reader : new BufferedReader(reader);
    }

    /**
     * Wraps the passed <code>writer</code> into a {@link BufferedWriter} object
     * and returns that. If the passed <code>writer</code> is already an
     * instance of {@link BufferedWriter} returns the same passed
     * <code>writer</code> reference as is (avoiding double wrapping).
     * 
     * @param writer the wrapee to be used for the buffering support
     * @return the passed <code>writer</code> decorated through a
     *         {@link BufferedWriter} object as wrapper
     */
    public static BufferedWriter buffered(Writer writer) {
        ObjectHelper.notNull(writer, "writer");
        return (writer instanceof BufferedWriter) ? (BufferedWriter)writer : new BufferedWriter(writer);
    }

    /**
     * A factory method which creates an {@link IOException} from the given
     * exception and message
     *
     * @deprecated IOException support nested exception in Java 1.6. Will be removed in Camel 3.0
     */
    @Deprecated
    public static IOException createIOException(Throwable cause) {
        return createIOException(cause.getMessage(), cause);
    }

    /**
     * A factory method which creates an {@link IOException} from the given
     * exception and message
     *
     * @deprecated IOException support nested exception in Java 1.6. Will be removed in Camel 3.0
     */
    @Deprecated
    public static IOException createIOException(String message, Throwable cause) {
        IOException answer = new IOException(message);
        answer.initCause(cause);
        return answer;
    }

    public static int copy(InputStream input, OutputStream output) throws IOException {
        return copy(input, output, DEFAULT_BUFFER_SIZE);
    }
    
    public static int copy(final InputStream input, final OutputStream output, int bufferSize) throws IOException {
        int avail = input.available();
        if (avail > 262144) {
            avail = 262144;
        }
        if (avail > bufferSize) {
            bufferSize = avail;
        }

        final byte[] buffer = new byte[bufferSize];
        int n = input.read(buffer);
        int total = 0;
        while (-1 != n) {
            output.write(buffer, 0, n);
            total += n;
            n = input.read(buffer);
        }
        output.flush();
        return total;
    }
    
    public static void copyAndCloseInput(InputStream input, OutputStream output) throws IOException {
        copyAndCloseInput(input, output, DEFAULT_BUFFER_SIZE);
    }
    
    public static void copyAndCloseInput(InputStream input, OutputStream output, int bufferSize) throws IOException {
        copy(input, output, bufferSize);
        close(input, null, LOG);
    }

    /**
     * Forces any updates to this channel's file to be written to the storage device that contains it.
     *
     * @param channel the file channel
     * @param name the name of the resource
     * @param log the log to use when reporting closure warnings
     */
    public static void force(FileChannel channel, String name, Logger log) {
        try {
            channel.force(true);
        } catch (Exception e) {
            if (log != null) {
                if (name != null) {
                    log.warn("Cannot force FileChannel: " + name + ". Reason: " + e.getMessage(), e);
                } else {
                    log.warn("Cannot force FileChannel. Reason: " + e.getMessage(), e);
                }
            }
        }

    }

    /**
     * Closes the given resource if it is available, logging any closing
     * exceptions to the given log
     *
     * @param closeable the object to close
     * @param name the name of the resource
     * @param log the log to use when reporting closure warnings
     */
    public static void close(Closeable closeable, String name, Logger log) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                if (log != null) {
                    if (name != null) {
                        log.warn("Cannot close: " + name + ". Reason: " + e.getMessage(), e);
                    } else {
                        log.warn("Cannot close. Reason: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    /**
     * Closes the given resource if it is available.
     *
     * @param closeable the object to close
     * @param name the name of the resource
     */
    public static void close(Closeable closeable, String name) {
        close(closeable, name, LOG);
    }

    /**
     * Closes the given resource if it is available.
     *
     * @param closeable the object to close
     */
    public static void close(Closeable closeable) {
        close(closeable, null, LOG);
    }

    /**
     * Closes the given resources if they are available.
     * 
     * @param closeables the objects to close
     */
    public static void close(Closeable... closeables) {
        for (Closeable closeable : closeables) {
            close(closeable);
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
                return IOHelper.normalizeCharset(charsetName);
            }
        }
        if (useDefault) {
            return getDefaultCharsetName();
        } else {
            return null;
        }
    }
    
    private static String getDefaultCharsetName() {
        return ObjectHelper.getSystemProperty(Exchange.DEFAULT_CHARSET_PROPERTY, "UTF-8");
    }
}
