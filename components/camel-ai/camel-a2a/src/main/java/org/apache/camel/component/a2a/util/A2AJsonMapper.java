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
package org.apache.camel.component.a2a.util;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared ObjectMapper for the A2A component: ISO 8601 dates, lenient unknown property handling.
 */
public final class A2AJsonMapper {

    // A2A is a wire protocol, so the JSON must not follow changes to the Jackson 3 defaults
    private static final ObjectMapper MAPPER = JsonMapper.builderWithJackson2Defaults()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private A2AJsonMapper() {
    }

    /**
     * Returns the shared immutable ObjectMapper. Use {@link ObjectMapper#rebuild()} to derive a customized mapper.
     *
     * @return the configured ObjectMapper
     */
    public static ObjectMapper instance() {
        return MAPPER;
    }

    /**
     * Converts a value as {@link ObjectMapper#convertValue(Object, Class)} does, and reports a failed conversion as an
     * {@link IllegalArgumentException}.
     *
     * @param  value                    the value to convert
     * @param  type                     the target type
     * @return                          the converted value
     * @throws IllegalArgumentException if the value cannot be converted
     */
    public static <T> T convertValue(Object value, Class<T> type) {
        try {
            return MAPPER.convertValue(value, type);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
