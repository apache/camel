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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Converter;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;

@Converter(generateLoader = true)
public final class DebeziumTypeConverter {

    private DebeziumTypeConverter() { }

    /**
     * Convert {@link Struct} to {@link HashMap}, this only works with flat fields and it doesn't handle nested structure.
     * Also as a result of the conversion, the schema data will be lost which is expected.
     *
     * @param struct
     * @return {@link Map}
     */
    @Converter
    public static Map<String, Object> toMap(final Struct struct) {
        final HashMap<String, Object> fieldsToValues = new HashMap<>();

        struct.schema().fields().forEach(field -> {
            try {
                fieldsToValues.put(field.name(), struct.get(field));
            } catch (DataException e) {
                fieldsToValues.put(field.name(), null);
            }
        });

        return fieldsToValues;
    }
}
