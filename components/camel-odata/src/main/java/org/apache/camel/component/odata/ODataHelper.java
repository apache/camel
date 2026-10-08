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
package org.apache.camel.component.odata;

import java.util.Map;

import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.Jsoner;

public final class ODataHelper {

    private ODataHelper() {
    }

    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof String string) {
            return string;
        }

        return Jsoner.serialize(value);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseJsonObject(String value) throws DeserializationException {
        if (value == null || value.isBlank()) {
            return Map.of();
        }

        Object parsed = Jsoner.deserialize(value);
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("Expected an OData JSON object response");
        }

        return (Map<String, Object>) parsed;
    }
}
