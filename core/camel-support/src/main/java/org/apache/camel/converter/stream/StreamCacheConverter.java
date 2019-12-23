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
package org.apache.camel.converter.stream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.util.IOHelper;

/**
 * A set of {@link Converter} methods for wrapping stream-based messages in a {@link StreamCache}
 * implementation to ensure message re-readability (eg multicasting, retrying)
 */
@Converter(generateLoader = true)
public final class StreamCacheConverter {

    /**
     * Utility classes should not have a public constructor.
     */
    private StreamCacheConverter() {
    }

    @Converter
    public static StreamCache convertToStreamCache(ByteArrayInputStream stream, Exchange exchange) throws IOException {
        return new ByteArrayInputStreamCache(stream);
    }

    @Converter
    public static StreamCache convertToStreamCache(InputStream stream, Exchange exchange) throws IOException {
        // transfer the input stream to a cached output stream, and then creates a new stream cache view
        // of the data, which ensures the input stream is cached and re-readable.
        CachedOutputStream cos = new CachedOutputStream(exchange);
        IOHelper.copyAndCloseInput(stream, cos);
        return cos.newStreamCache();
    }

    @Converter
    public static StreamCache convertToStreamCache(CachedOutputStream cos, Exchange exchange) throws IOException {
        return cos.newStreamCache();
    }

    @Converter
    public static StreamCache convertToStreamCache(Reader reader, Exchange exchange) throws IOException {
        String data = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, reader);
        return new ReaderCache(data);
    }

    @Converter
    public static byte[] convertToByteArray(StreamCache cache, Exchange exchange) throws IOException {
        // lets serialize it as a byte array
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        cache.writeTo(os);
        return os.toByteArray();
    }

    @Converter
    public static ByteBuffer convertToByteBuffer(StreamCache cache, Exchange exchange) throws IOException {
        byte[] array = convertToByteArray(cache, exchange);
        return ByteBuffer.wrap(array);
    }

}
