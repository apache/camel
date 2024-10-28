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
package org.apache.camel.component.kamelet.utils.transform;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.util.ObjectHelper;

public class MaskField {

    private static final Map<Class<?>, Function<String, ?>> MAPPING_FUNC = new HashMap<>();
    private static final Map<Class<?>, Object> BASIC_MAPPING = new HashMap<>();

    static {
        BASIC_MAPPING.put(Boolean.class, Boolean.FALSE);
        BASIC_MAPPING.put(Byte.class, (byte) 0);
        BASIC_MAPPING.put(Short.class, (short) 0);
        BASIC_MAPPING.put(Integer.class, 0);
        BASIC_MAPPING.put(Long.class, 0L);
        BASIC_MAPPING.put(Float.class, 0f);
        BASIC_MAPPING.put(Double.class, 0d);
        BASIC_MAPPING.put(BigInteger.class, BigInteger.ZERO);
        BASIC_MAPPING.put(BigDecimal.class, BigDecimal.ZERO);
        BASIC_MAPPING.put(Date.class, new Date(0));
        BASIC_MAPPING.put(String.class, "");

        MAPPING_FUNC.put(Byte.class, Byte::parseByte);
        MAPPING_FUNC.put(Short.class, Short::parseShort);
        MAPPING_FUNC.put(Integer.class, Integer::parseInt);
        MAPPING_FUNC.put(Long.class, Long::parseLong);
        MAPPING_FUNC.put(Float.class, Float::parseFloat);
        MAPPING_FUNC.put(Double.class, Double::parseDouble);
        MAPPING_FUNC.put(String.class, Function.identity());
        MAPPING_FUNC.put(BigDecimal.class, BigDecimal::new);
        MAPPING_FUNC.put(BigInteger.class, BigInteger::new);
    }

    public JsonNode process(
            @ExchangeProperty("fields") String fields, @ExchangeProperty("replacement") String replacement, Exchange ex)
            throws InvalidPayloadException {
        ObjectMapper mapper = new ObjectMapper();
        List<String> splittedFields = new ArrayList<>();
        JsonNode jsonNodeBody = ex.getMessage().getBody(JsonNode.class);
        Map<Object, Object> body = mapper.convertValue(jsonNodeBody, new TypeReference<Map<Object, Object>>() {
        });
        if (ObjectHelper.isNotEmpty(fields)) {
            splittedFields = Arrays.stream(fields.split(",")).collect(Collectors.toList());
        }

        Map<Object, Object> updatedBody = new HashMap<>();
        for (Map.Entry<Object, Object> entry : body.entrySet()) {
            final String fieldName = (String) entry.getKey();
            final Object origFieldValue = entry.getValue();
            updatedBody.put(fieldName,
                    filterNames(fieldName, splittedFields) ? masked(origFieldValue, replacement) : origFieldValue);
        }
        if (!updatedBody.isEmpty()) {
            return mapper.valueToTree(updatedBody);
        } else {
            return mapper.valueToTree(body);
        }
    }

    boolean filterNames(String fieldName, List<String> splittedFields) {
        return splittedFields.contains(fieldName);
    }

    private Object masked(Object value, String replacement) {
        if (value == null) {
            return null;
        }
        return replacement == null ? maskWithNullValue(value) : maskWithCustomReplacement(value, replacement);
    }

    private static Object maskWithCustomReplacement(Object value, String replacement) {
        Function<String, ?> replacementMapper = MAPPING_FUNC.get(value.getClass());
        if (replacementMapper == null) {
            throw new IllegalArgumentException(
                    "Unable to mask value of type " + value.getClass() + " with custom replacement.");
        }
        try {
            return replacementMapper.apply(replacement);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Unable to convert " + replacement + " (" + replacement.getClass() + ") to number", ex);
        }
    }

    private static Object maskWithNullValue(Object value) {
        Object maskedValue = BASIC_MAPPING.get(value.getClass());
        if (maskedValue == null) {
            if (value instanceof List)
                maskedValue = Collections.emptyList();
            else if (value instanceof Map)
                maskedValue = Collections.emptyMap();
            else
                throw new IllegalArgumentException("Unable to mask value of type: " + value.getClass());
        }
        return maskedValue;
    }
}
