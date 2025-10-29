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
package org.apache.camel.component.google.bigquery.sql;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.cloud.bigquery.*;
import org.apache.camel.support.RowMapper;

/**
 * Maps BigQuery {@link FieldValueList} rows to {@code Map<String, Object>}.
 * <p>
 * Handles all BigQuery data types:
 * <ul>
 * <li>Primitives (INT64, STRING, FLOAT64, BOOL, etc.) → Java equivalents</li>
 * <li>NULL → null</li>
 * <li>RECORD (STRUCT) → nested Map (recursive)</li>
 * <li>REPEATED (ARRAY) → List (recursive)</li>
 * <li>RANGE → Map with range values</li>
 * </ul>
 */
public class FieldValueListMapper implements RowMapper<FieldValueList, Map<String, Object>> {
    private final FieldList schema;

    public FieldValueListMapper(FieldList schema) {
        this.schema = schema;
    }

    @Override
    public Map<String, Object> map(FieldValueList row) {
        return map(schema, row);
    }

    private Map<String, Object> map(FieldList schema, FieldValueList row) {
        Map<String, Object> rowMap = new HashMap<>();
        for (int i = 0; i < schema.size(); i++) {
            var field = schema.get(i);
            rowMap.put(field.getName(), convertValue(field, row.get(i)));
        }
        return rowMap;
    }

    private Object convertValue(Field field, FieldValue fieldValue) {
        return switch (fieldValue.getAttribute()) {
            case RECORD -> map(field.getSubFields(), fieldValue.getRecordValue());
            case REPEATED -> fieldValue.getRepeatedValue().stream()
                    .map(element -> convertValue(field, element))
                    .collect(Collectors.toList());
            case RANGE -> fieldValue.getRangeValue().getValues();
            case PRIMITIVE -> fieldValue.getValue();
        };
    }
}
