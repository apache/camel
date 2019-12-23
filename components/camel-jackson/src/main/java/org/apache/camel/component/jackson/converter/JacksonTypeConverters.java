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
package org.apache.camel.component.jackson.converter;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.apache.camel.CamelContext;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.TypeConverterRegistry;

/**
 * Jackson {@link org.apache.camel.TypeConverter} that allows converting json
 * to/from POJOs and other types. <br/>
 * This implementation uses a fallback converter.
 * <p/>
 * The converter is disabled by default. To enable then set the property
 * {@link JacksonConstants#ENABLE_TYPE_CONVERTER} to <tt>true</tt> on
 * {@link CamelContext#getGlobalOptions()}. <br/>
 * The option {@link JacksonConstants#TYPE_CONVERTER_TO_POJO} can be used to
 * allow converting to POJO types. By default the converter only attempts to
 * convert to primitive types such as String and numbers. To convert to any
 * kind, then enable this by setting
 * {@link JacksonConstants#TYPE_CONVERTER_TO_POJO} to <tt>true</tt> on
 * {@link CamelContext#getGlobalOptions()}.
 */
@Converter(generateLoader = true)
public final class JacksonTypeConverters {

    private final ObjectMapper defaultMapper;
    private boolean init;
    private boolean enabled;
    private boolean toPojo;

    public JacksonTypeConverters() {
        defaultMapper = new ObjectMapper();
        // Enables JAXB processing so we can easily convert JAXB annotated pojos
        // also
        JaxbAnnotationModule module = new JaxbAnnotationModule();
        defaultMapper.registerModule(module);
    }

    @Converter(fallback = true)
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value, TypeConverterRegistry registry) throws Exception {

        // only do this if enabled (disabled by default)
        if (!init && exchange != null) {
            // init to see if this is enabled
            String text = exchange.getContext().getGlobalOptions().get(JacksonConstants.ENABLE_TYPE_CONVERTER);
            if (text != null) {
                text = exchange.getContext().resolvePropertyPlaceholders(text);
                enabled = "true".equalsIgnoreCase(text);
            }

            // pojoOnly is enabled by default
            text = exchange.getContext().getGlobalOptions().get(JacksonConstants.TYPE_CONVERTER_TO_POJO);
            if (text != null) {
                text = exchange.getContext().resolvePropertyPlaceholders(text);
                toPojo = "true".equalsIgnoreCase(text);
            }

            init = true;
        }

        if (!enabled) {
            return null;
        }

        if (!toPojo && isNotPojoType(type)) {
            return null;
        }

        if (exchange != null) {
            ObjectMapper mapper = resolveObjectMapper(exchange.getContext().getRegistry());

            // favor use write/read operations as they are higher level than the
            // convertValue

            // if we want to convert to a String or byte[] then use write
            // operation
            if (String.class.isAssignableFrom(type)) {
                String out = mapper.writeValueAsString(value);
                return type.cast(out);
            } else if (byte[].class.isAssignableFrom(type)) {
                byte[] out = mapper.writeValueAsBytes(value);
                return type.cast(out);
            } else if (mapper.canSerialize(type) && !Enum.class.isAssignableFrom(type)) {
                // if the source value type is readable by the mapper then use
                // its read operation
                if (String.class.isAssignableFrom(value.getClass())) {
                    return mapper.readValue((String)value, type);
                } else if (byte[].class.isAssignableFrom(value.getClass())) {
                    return mapper.readValue((byte[])value, type);
                } else if (File.class.isAssignableFrom(value.getClass())) {
                    return mapper.readValue((File)value, type);
                } else if (InputStream.class.isAssignableFrom(value.getClass())) {
                    return mapper.readValue((InputStream)value, type);
                } else if (Reader.class.isAssignableFrom(value.getClass())) {
                    return mapper.readValue((Reader)value, type);
                } else {
                    // fallback to generic convert value
                    return mapper.convertValue(value, type);
                }
            }
        }

        // Just return null to let other fallback converter to do the job
        return null;
    }

    /**
     * Whether the type is NOT a pojo type but only a set of simple types such
     * as String and numbers.
     */
    private static boolean isNotPojoType(Class<?> type) {
        boolean isString = String.class.isAssignableFrom(type);
        boolean isNumber = Number.class.isAssignableFrom(type) || int.class.isAssignableFrom(type) || long.class.isAssignableFrom(type) || short.class.isAssignableFrom(type)
                           || char.class.isAssignableFrom(type) || float.class.isAssignableFrom(type) || double.class.isAssignableFrom(type);
        return isString || isNumber;
    }

    private ObjectMapper resolveObjectMapper(Registry registry) {
        Set<ObjectMapper> mappers = registry.findByType(ObjectMapper.class);
        if (mappers.size() == 1) {
            return mappers.iterator().next();
        }
        return defaultMapper;
    }

}
