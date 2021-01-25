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
package org.apache.camel.component.stitch.client;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonUtils {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    public static String convertMapToJson(final Map<String, Object> inputMap) {
        try {
            return MAPPER.writeValueAsString(inputMap);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("Error occurred writing data map to JSON.", exception);
        }
    }

    public static Map<String, Object> convertJsonToMap(final String jsonString) {
        try {
            return MAPPER.readValue(jsonString, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("Error occurred writing JSON to Map.", exception);
        }
    }
}
