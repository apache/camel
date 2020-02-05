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
package org.apache.camel.util.xml;

import java.io.IOException;
import java.io.Serializable;

import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;

import static org.apache.camel.converter.stream.StreamCacheConverter.convertToByteArray;

/**
 * A set of {@link Converter} methods for wrapping stream-based messages in a {@link StreamCache}
 * implementation to ensure message re-readability (eg multicasting, retrying)
 */
@Converter(generateLoader = true)
public final class StreamSourceConverter {

    /**
     * Utility classes should not have a public constructor.
     */
    private StreamSourceConverter() {
    }

    @Converter
    public static StreamCache convertToStreamCache(StreamSource source, Exchange exchange) throws IOException {
        return new StreamSourceCache(source, exchange);
    }

    @Converter
    public static StreamCache convertToStreamCache(BytesSource source) {
        //no need to do stream caching for a BytesSource
        return null;
    }

    @Converter
    public static StreamCache convertToStreamCache(SAXSource source, Exchange exchange) throws TransformerException {
        String data = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, source);
        return new SourceCache(data);
    }

    @Converter
    public static Serializable convertToSerializable(StreamCache cache, Exchange exchange) throws IOException {
        byte[] data = convertToByteArray(cache, exchange);
        return new BytesSource(data);
    }

}
