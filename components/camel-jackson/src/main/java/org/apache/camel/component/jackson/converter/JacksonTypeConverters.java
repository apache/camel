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
package org.apache.camel.component.jackson.converter;

import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.apache.camel.Exchange;
import org.apache.camel.FallbackConverter;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.TypeConverterRegistry;

public final class JacksonTypeConverters {

    private final ObjectMapper defaultMapper;
    private boolean init;
    private Boolean enabled;
    private boolean pojoOnly = true;

    public JacksonTypeConverters() {
        defaultMapper = new ObjectMapper();
        // Enables JAXB processing
        JaxbAnnotationModule module = new JaxbAnnotationModule();
        defaultMapper.registerModule(module);
    }

    @FallbackConverter
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value, TypeConverterRegistry registry) throws Exception {

        // only do this if enabled
        if (!init && exchange != null) {
            // init to see if this is enabled
            String text = exchange.getContext().getProperties().get(JacksonConstants.ENABLE_TYPE_CONVERTER);
            enabled = "true".equalsIgnoreCase(text);

            // pojo only by default
            text = exchange.getContext().getProperties().get(JacksonConstants.TYPE_CONVERTER_POJO_ONLY);
            if (text != null) {
                pojoOnly = "true".equalsIgnoreCase(text);
            }

            init = true;
        }

        if (enabled == null || !enabled) {
            return null;
        }

        if (pojoOnly && isNotPojoType(type)) {
            return null;
        }

        if (exchange != null) {
            ObjectMapper mapper = resolveObjectMapper(exchange.getContext().getRegistry());
            if (String.class.isAssignableFrom(type)) {
                String out = mapper.writeValueAsString(value);
                return type.cast(out);
            } else if (byte[].class.isAssignableFrom(type)) {
                byte[] out = mapper.writeValueAsBytes(value);
                return type.cast(out);
            } else if (mapper.canSerialize(type)) {
                // TODO: favor using mapper readValue
                // if the input is string or input stream
                if (String.class.isAssignableFrom(value.getClass())) {
                    return mapper.readValue((String) value, type);
                }
                return mapper.convertValue(value, type);
            }
        }

        // Just return null to let other fallback converter to do the job
        return null;
    }

    private static boolean isNotPojoType(Class<?> type) {
        boolean isString = String.class.isAssignableFrom(type);
        boolean isNumber = Number.class.isAssignableFrom(type)
                || int.class.isAssignableFrom(type) || long.class.isAssignableFrom(type)
                || short.class.isAssignableFrom(type) || char.class.isAssignableFrom(type)
                || float.class.isAssignableFrom(type) || double.class.isAssignableFrom(type);
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
