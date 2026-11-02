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
package org.apache.camel.component.debezium;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Converter;
import org.apache.kafka.connect.data.Struct;

@Converter(generateLoader = true)
public final class DebeziumTypeConverter {

    private DebeziumTypeConverter() {
    }

    /**
     * Convert {@link Struct} to {@link HashMap}. A nested {@link Struct} is converted to a {@link Map} too, also inside
     * a list (an array field) or as the value of a map (a map field). As a result of the conversion, the schema data
     * will be lost which is expected.
     *
     * @param  struct
     * @return        {@link Map}
     */
    @Converter
    public static Map<String, Object> toMap(final Struct struct) {
        final HashMap<String, Object> fieldsToValues = new HashMap<>();

        struct.schema().fields().forEach(field -> fieldsToValues.put(field.name(), convertValue(struct.get(field))));

        return fieldsToValues;
    }

    private static Object convertValue(final Object value) {
        // recursive call if we have nested structs
        if (value instanceof Struct struct) {
            return toMap(struct);
        } else if (value instanceof List<?> list) {
            final List<Object> answer = new ArrayList<>(list.size());
            list.forEach(element -> answer.add(convertValue(element)));
            return answer;
        } else if (value instanceof Map<?, ?> map) {
            final Map<Object, Object> answer = new HashMap<>();
            map.forEach((key, element) -> answer.put(convertValue(key), convertValue(element)));
            return answer;
        }
        return value;
    }
}
