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
package org.apache.camel.component.jackson;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Marshal POJOs to JSON and back using Jackson.
 */
@Dataformat("jackson")
@Metadata(excludeProperties = "library,permissions,dateFormatPattern")
public class JacksonDataFormat extends AbstractJacksonDataFormat {

    private static final Logger LOG = LoggerFactory.getLogger(JacksonDataFormat.class);

    /**
     * Use the default Jackson {@link ObjectMapper} and {@link Object}
     */
    public JacksonDataFormat() {
    }

    /**
     * Use the default Jackson {@link ObjectMapper} and with a custom unmarshal type
     *
     * @param unmarshalType the custom unmarshal type
     */
    public JacksonDataFormat(Class<?> unmarshalType) {
        super(unmarshalType);
    }

    /**
     * Use the default Jackson {@link ObjectMapper} and with a custom unmarshal type and JSON view
     *
     * @param unmarshalType the custom unmarshal type
     * @param jsonView      marker class to specify properties to be included during marshalling. See also
     */
    public JacksonDataFormat(Class<?> unmarshalType, Class<?> jsonView) {
        super(unmarshalType, jsonView);
    }

    /**
     * Use a custom Jackson mapper and an unmarshal type
     *
     * @param mapper        the custom mapper
     * @param unmarshalType the custom unmarshal type
     */
    public JacksonDataFormat(ObjectMapper mapper, Class<?> unmarshalType) {
        super(mapper, unmarshalType);
    }

    /**
     * Use a custom Jackson mapper, unmarshal type and JSON view
     *
     * @param mapper        the custom mapper
     * @param unmarshalType the custom unmarshal type
     * @param jsonView      marker class to specify properties to be included during marshalling. See also
     */
    public JacksonDataFormat(ObjectMapper mapper, Class<?> unmarshalType, Class<?> jsonView) {
        super(mapper, unmarshalType, jsonView);
    }

    @Override
    public String getDataFormatName() {
        return "jackson";
    }

    @Override
    protected ObjectMapper createNewObjectMapper() {
        // Enable BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES by default as defense-in-depth against gadget-chain
        // deserialization when polymorphic typing is enabled, consistent with transform/Json.java.
        ObjectMapper om = JsonMapper.builder()
                .enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
                .build();
        int len = getMaxStringLength();
        if (len > 0) {
            LOG.debug("Creating ObjectMapper with maxStringLength: {}", len);
            om.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxStringLength(len).build());
        }
        return om;
    }

    @Override
    protected Class<? extends ObjectMapper> getObjectMapperClass() {
        return ObjectMapper.class;
    }

    @Override
    protected String getDefaultContentType() {
        return "application/json";
    }

    /**
     * A body that is already the JSON text is written as it is (CAMEL-25329): a file, a stream or bytes are the
     * serialized form of a payload, never an object to serialize (Jackson would fail on the stream and file, and write
     * the bytes as base64), and a String holding a JSON object or array would come out as one JSON string. Any other
     * String, and every object, is marshalled as before.
     */
    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        if (writeAsIs(exchange, graph, stream)) {
            if (isContentTypeHeader()) {
                exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, getDefaultContentType());
            }
            return;
        }
        super.marshal(exchange, graph, stream);
    }

    private static boolean writeAsIs(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        if (graph instanceof byte[] bytes) {
            stream.write(bytes);
        } else if (graph instanceof StreamCache cache) {
            cache.writeTo(stream);
        } else if (graph instanceof InputStream is) {
            is.transferTo(stream);
        } else if (graph instanceof WrappedFile<?>) {
            try (InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange,
                    graph)) {
                is.transferTo(stream);
            }
        } else if (graph instanceof String text && isJsonText(text)) {
            stream.write(text.getBytes(StandardCharsets.UTF_8));
        } else {
            return false;
        }
        return true;
    }

    /** A JSON object or array, by its first and last character; no parsing. */
    static boolean isJsonText(String text) {
        String t = text.strip();
        return t.length() >= 2 && (t.startsWith("{") && t.endsWith("}") || t.startsWith("[") && t.endsWith("]"));
    }
}
