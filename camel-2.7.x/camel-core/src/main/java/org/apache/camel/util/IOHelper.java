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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

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
     * A factory method which creates an {@link IOException} from the given
     * exception and message
     *
     * @deprecated IOException support nested exception in Java 1.6.
     */
    @Deprecated
    public static IOException createIOException(Throwable cause) {
        return createIOException(cause.getMessage(), cause);
    }

    /**
     * A factory method which creates an {@link IOException} from the given
     * exception and message
     *
     * @deprecated IOException support nested exception in Java 1.6.
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
        copy(input, output);
        close(input, null, LOG);
    }
    
    public static void copyAndCloseInput(InputStream input, OutputStream output, int bufferSize) throws IOException {
        copy(input, output, bufferSize);
        close(input, null, LOG);
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

}
