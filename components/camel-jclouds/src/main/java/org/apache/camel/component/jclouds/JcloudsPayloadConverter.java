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
import javax.xml.transform.stream.StreamSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.FallbackConverter;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.converter.stream.StreamSourceCache;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.util.IOHelper;
import org.jclouds.io.Payload;
import org.jclouds.io.payloads.ByteArrayPayload;
import org.jclouds.io.payloads.FilePayload;
import org.jclouds.io.payloads.InputStreamPayload;
import org.jclouds.io.payloads.StringPayload;

@Converter
public final class JcloudsPayloadConverter {

    private JcloudsPayloadConverter() {
        //Utility Class
    }

    @Converter
    public static Payload toPayload(byte[] bytes) {
        return new ByteArrayPayload(bytes);
    }

    @Converter
    public static Payload toPayload(String str) {
        return new StringPayload(str);
    }

    @Converter
    public static Payload toPayload(File file) {
        return new FilePayload(file);
    }

    @Converter
    public static Payload toPayload(InputStream is, Exchange exchange) throws IOException {
        if (is.markSupported()) {
            InputStreamPayload payload = new InputStreamPayload(is);
            long contentLength = ByteStreams.length(payload);
            is.reset();
            payload.getContentMetadata().setContentLength(contentLength);
            return payload;
        } else {
            CachedOutputStream cos = new CachedOutputStream(exchange);
            IOHelper.copy(is, cos);
            return toPayload(cos.getWrappedInputStream(), exchange);
        }
    }

    @Converter
    public static Payload toPayload(StreamSource source, Exchange exchange) throws IOException {
        return toPayload(new StreamSourceCache(source, exchange));
    }

    @Converter
    public static Payload toPayload(final StreamSourceCache cache) throws IOException {
        long contentLength = ByteStreams.length(new InputSupplier<InputStream>() {
            @Override
            public InputStream getInput() throws IOException {
                return cache.getInputStream();
            }
        });
        cache.reset();
        InputStreamPayload payload = new InputStreamPayload(cache.getInputStream());
        payload.getContentMetadata().setContentLength(contentLength);
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
