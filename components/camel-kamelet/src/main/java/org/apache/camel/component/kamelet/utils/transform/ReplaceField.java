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

import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.util.ObjectHelper;

public class ReplaceField {

    public JsonNode process(
            @ExchangeProperty("enabled") String enabled, @ExchangeProperty("disabled") String disabled,
            @ExchangeProperty("renames") String renames, Exchange ex)
            throws InvalidPayloadException {
        ObjectMapper mapper = new ObjectMapper();
        List<String> enabledFields = new ArrayList<>();
        List<String> disabledFields = new ArrayList<>();
        List<String> renameFields = new ArrayList<>();
        JsonNode jsonNodeBody = ex.getMessage().getBody(JsonNode.class);
        Map<Object, Object> body = mapper.convertValue(jsonNodeBody, new TypeReference<Map<Object, Object>>() {
        });
        if (ObjectHelper.isNotEmpty(enabled) && !enabled.equalsIgnoreCase("all")) {
            enabledFields = Arrays.stream(enabled.split(",")).collect(Collectors.toList());
        }
        if (ObjectHelper.isNotEmpty(disabled) && !disabled.equalsIgnoreCase("none")) {
            disabledFields = Arrays.stream(disabled.split(",")).collect(Collectors.toList());
        }
        if (ObjectHelper.isNotEmpty(disabled)) {
            renameFields = Arrays.stream(renames.split(",")).collect(Collectors.toList());
        }
        Map<Object, Object> updatedBody = new HashMap<>();

        if (ObjectHelper.isNotEmpty(renameFields)) {
            Map<String, String> renamingMap = parseNames(renameFields);

            for (Map.Entry<Object, Object> entry : body.entrySet()) {
                final String fieldName = (String) entry.getKey();
                if (filterNames(fieldName, enabledFields, disabledFields)) {
                    final Object fieldValue = entry.getValue();
                    updatedBody.put(renameOptional(fieldName, renamingMap), fieldValue);
                }
            }
        }
        if (!updatedBody.isEmpty()) {
            return mapper.valueToTree(updatedBody);
        } else {
            return mapper.valueToTree(body);
        }
    }

    boolean filterNames(String fieldName, List<String> enabledFields, List<String> disabledFields) {
        return !disabledFields.contains(fieldName) && (enabledFields.isEmpty() || enabledFields.contains(fieldName));
    }

    static Map<String, String> parseNames(List<String> mappings) {
        final Map<String, String> m = new HashMap<>();
        for (String mapping : mappings) {
            final String[] parts = mapping.split(":");
            m.put(parts[0], parts[1]);
        }
        return m;
    }

    String renameOptional(String fieldName, Map<String, String> renames) {
        final String mapping = renames.get(fieldName);
        return mapping == null ? fieldName : mapping;
    }

}
