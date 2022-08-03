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
package org.apache.camel.converter.aries;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.hyperledger.aries.config.GsonConfig;

class AbstractAriesConverter {

    static final Gson GSON = GsonConfig.defaultConfig();

    protected AbstractAriesConverter() {
    }

    static <T> T toAries(JsonObject jsonObj, Class<T> type) {
        T result = null;
        if (filterFields(type, jsonObj)) {
            result = GSON.fromJson(jsonObj, type);
        }
        return result;
    }

    static <T> T toAries(String json, Class<T> type) {
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            return toAries(obj, type);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    static <T> T toAries(Map<String, Object> map, Class<T> type) {
        String json = GSON.toJson(map);
        return toAries(json, type);
    }

    static String toSnakeCase(String instr) {
        return instr.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    static boolean filterFields(Class<?> type, JsonObject json) {

        // Convert target object field names to snake case
        List<String> acceptedFields = Arrays.asList(type.getDeclaredFields()).stream()
                .map(f -> toSnakeCase(f.getName()))
                .collect(Collectors.toList());

        // Filter unexpected properties
        new HashSet<>(json.keySet()).stream().forEach(k -> {
            if (!acceptedFields.contains(k)) {
                json.remove(k);
            }
        });

        return !json.keySet().isEmpty();
    }
}
