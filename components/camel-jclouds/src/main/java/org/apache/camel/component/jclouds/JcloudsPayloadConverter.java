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
package org.apache.camel.component.jclouds;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;

import javax.xml.transform.stream.StreamSource;

import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.FallbackConverter;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.converter.stream.StreamSourceCache;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.jclouds.io.Payload;
import org.jclouds.io.payloads.ByteSourcePayload;
import org.jclouds.io.payloads.InputStreamPayload;

@Converter
public final class JcloudsPayloadConverter {

    private JcloudsPayloadConverter() {
        //Utility Class
    }

    @Converter
    public static Payload toPayload(byte[] bytes) {
        return new ByteSourcePayload(ByteSource.wrap(bytes));
    }

    @Converter
    public static Payload toPayload(String str, Exchange ex) throws UnsupportedEncodingException {
        return toPayload(str.getBytes(IOHelper.getCharsetName(ex)));
    }
    
    public static Payload toPayload(String str) throws UnsupportedEncodingException {
        return toPayload(str, null);
    }

    @Converter
    public static Payload toPayload(File file) {
        return new ByteSourcePayload(Files.asByteSource(file));
    }
    
    protected static Payload setContentMetadata(Payload payload, Exchange exchange) {
        // Just add an NPE check on the payload
        if (exchange == null) {
            return payload;
        }
        
        String contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
        String contentEncoding = exchange.getIn().getHeader(Exchange.CONTENT_ENCODING, String.class);
        String contentDisposition = exchange.getIn().getHeader(JcloudsConstants.CONTENT_DISPOSITION, String.class);
        String contentLanguage = exchange.getIn().getHeader(JcloudsConstants.CONTENT_LANGUAGE, String.class);
        Date payloadExpires = exchange.getIn().getHeader(JcloudsConstants.PAYLOAD_EXPIRES, Date.class);
        
        if (ObjectHelper.isNotEmpty(contentType)) {
            payload.getContentMetadata().setContentType(contentType);
        }
        
        if (ObjectHelper.isNotEmpty(contentEncoding)) {
            payload.getContentMetadata().setContentEncoding(contentEncoding);
        }
        
        if (ObjectHelper.isNotEmpty(contentDisposition)) {
            payload.getContentMetadata().setContentDisposition(contentDisposition);
        }
        
        if (ObjectHelper.isNotEmpty(contentLanguage)) {
            payload.getContentMetadata().setContentLanguage(contentLanguage);
        }
        
        if (ObjectHelper.isNotEmpty(payloadExpires)) {
            payload.getContentMetadata().setExpires(payloadExpires);
        }
        return payload;
    }

    @Converter
    public static Payload toPayload(final InputStream is, Exchange exchange) throws IOException {
        InputStreamPayload payload = new InputStreamPayload(is);
        // only set the contentlength if possible
        if (is.markSupported()) {
            long contentLength = ByteStreams.length(new InputSupplier<InputStream>() {
                @Override
                public InputStream getInput() throws IOException {
                    return is;
                }
            });
            is.reset();
            payload.getContentMetadata().setContentLength(contentLength);
        }
        return payload;
    }

    @Converter
    public static Payload toPayload(StreamSource source, Exchange exchange) throws IOException {
        return toPayload(new StreamSourceCache(source, exchange), exchange);
    }

    @Converter
    public static Payload toPayload(final StreamSourceCache cache, Exchange exchange) throws IOException {
        long contentLength = ByteStreams.length(new InputSupplier<InputStream>() {
            @Override
            public InputStream getInput() throws IOException {
                return cache.getInputStream();
            }
        });
        cache.reset();
        InputStreamPayload payload = new InputStreamPayload(cache.getInputStream());
        payload.getContentMetadata().setContentLength(contentLength);
        setContentMetadata(payload, exchange);
        return payload;
    }

    @FallbackConverter
    @SuppressWarnings("unchecked")
    public static <T extends Payload> T convertTo(Class<T> type, Exchange exchange, Object value, TypeConverterRegistry registry) throws IOException {
        Class<?> sourceType = value.getClass();
        if (GenericFile.class.isAssignableFrom(sourceType)) {
            GenericFile<?> genericFile = (GenericFile<?>) value;
            if (genericFile.getFile() != null) {
                Class<?> genericFileType = genericFile.getFile().getClass();
                TypeConverter converter = registry.lookup(Payload.class, genericFileType);
                if (converter != null) {
                    return (T) converter.convertTo(Payload.class, genericFile.getFile());
                }
            }
        }
        return null;
    }
}
