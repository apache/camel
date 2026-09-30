/*
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
package org.apache.camel.support;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.builder.OutputStreamBuilder;
import org.apache.camel.util.IOHelper;

/**
 * Helper for components that need to know the length of a (possibly big) message body, for example to upload it.
 * <p/>
 * The length is determined without reading the body. When the length cannot be determined that way, the body can be
 * copied with {@link #cacheStream(Exchange, InputStream)}, which uses stream caching and therefore spools big payloads
 * to disk when spooling is enabled, instead of loading them into memory.
 */
public final class PayloadHelper {

    private PayloadHelper() {
    }

    /**
     * Gets the length of the message body, without reading the body.
     *
     * @param  message the message
     * @return         the length in bytes, or <tt>-1</tt> if the length cannot be determined without reading the body
     */
    public static long getBodyLength(Message message) {
        Object body = message.getBody();
        if (body instanceof WrappedFile<?> wf) {
            long length = wf.getFileLength();
            if (length > 0) {
                return length;
            }
            Object file = wf.getFile();
            body = file instanceof File || file instanceof Path ? file : wf.getBody();
        }
        return getLength(body);
    }

    /**
     * Gets the length of the given value, without reading it.
     * <p/>
     * The length is known for files, byte arrays, stream caches of bytes, and input streams backed by a byte array or a
     * file.
     *
     * @param  value the value, such as a message body or an input stream
     * @return       the length in bytes, or <tt>-1</tt> if the length cannot be determined without reading the value
     */
    public static long getLength(Object value) {
        try {
            if (value instanceof File file) {
                return file.isFile() ? file.length() : -1;
            } else if (value instanceof Path path) {
                return Files.isRegularFile(path) ? Files.size(path) : -1;
            } else if (value instanceof byte[] bytes) {
                return bytes.length;
            } else if (value instanceof StreamCache cache && value instanceof InputStream && cache.length() > 0) {
                // only a stream cache that is a byte stream has a length in bytes (a reader cache counts characters),
                // and a stream cache may return 0 when the length cannot be computed
                return cache.length();
            } else if (value instanceof ByteArrayInputStream is) {
                return is.available();
            } else if (value instanceof FileInputStream fis) {
                FileChannel channel = fis.getChannel();
                return channel.size() - channel.position();
            }
        } catch (IOException | UnsupportedOperationException e) {
            // the length cannot be determined
        }
        return -1;
    }

    /**
     * Copies the given input stream so its length is known.
     * <p/>
     * The stream is copied using stream caching (when enabled), so a big payload is spooled to disk if spooling is
     * enabled, instead of being loaded into memory. The copy is released when the exchange is complete.
     *
     * @param  exchange    the exchange
     * @param  is          the input stream to copy, which is closed afterwards
     * @return             the copy, which is a stream whose length can be determined with {@link #getLength(Object)}
     * @throws IOException if the stream cannot be copied
     */
    public static InputStream cacheStream(Exchange exchange, InputStream is) throws IOException {
        OutputStreamBuilder builder = OutputStreamBuilder.withExchange(exchange);
        IOHelper.copyAndCloseInput(is, builder);
        Object answer = builder.build();
        if (answer instanceof InputStream cached) {
            return cached;
        }
        return new ByteArrayInputStream((byte[]) answer);
    }
}
