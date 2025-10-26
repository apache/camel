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
package org.apache.camel.component.google.bigquery.unit.sql;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.StandardSQLTypeName;
import org.apache.camel.component.google.bigquery.sql.FieldValueListMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FieldValueListMapperTest {

    @Test
    public void mapSimpleRow() {
        Field idField = Field.of("id", StandardSQLTypeName.INT64);
        Field nameField = Field.of("name", StandardSQLTypeName.STRING);
        FieldList fieldList = FieldList.of(idField, nameField);

        FieldValueList row = FieldValueList.of(
                Arrays.asList(
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, 123L),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "John Doe")),
                fieldList);

        FieldValueListMapper mapper = new FieldValueListMapper(fieldList);
        Map<String, Object> result = mapper.map(row);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(123L, result.get("id"));
        assertEquals("John Doe", result.get("name"));
    }

    @Test
    public void mapRowWithNullValue() {
        Field idField = Field.of("id", StandardSQLTypeName.INT64);
        Field nameField = Field.of("name", StandardSQLTypeName.STRING);
        Field ageField = Field.of("age", StandardSQLTypeName.INT64);
        FieldList fieldList = FieldList.of(idField, nameField, ageField);

        FieldValueList row = FieldValueList.of(
                Arrays.asList(
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, 123L),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "John Doe"),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, null)),
                fieldList);

        FieldValueListMapper mapper = new FieldValueListMapper(fieldList);
        Map<String, Object> result = mapper.map(row);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.containsKey("age"), "Should contain age key even if value is null");
        assertNull(result.get("age"), "Age value should be null");
        assertEquals(123L, result.get("id"));
        assertEquals("John Doe", result.get("name"));
    }

    @Test
    public void mapRowWithAllNullValues() {
        Field col1Field = Field.of("col1", StandardSQLTypeName.STRING);
        Field col2Field = Field.of("col2", StandardSQLTypeName.STRING);
        FieldList fieldList = FieldList.of(col1Field, col2Field);

        FieldValueList row = FieldValueList.of(
                Arrays.asList(
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, null),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, null)),
                fieldList);

        FieldValueListMapper mapper = new FieldValueListMapper(fieldList);
        Map<String, Object> result = mapper.map(row);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertNull(result.get("col1"));
        assertNull(result.get("col2"));
    }

    @Test
    public void mapRowWithMultipleDataTypes() {
        Field intField = Field.of("int_col", StandardSQLTypeName.INT64);
        Field stringField = Field.of("string_col", StandardSQLTypeName.STRING);
        Field floatField = Field.of("float_col", StandardSQLTypeName.FLOAT64);
        Field boolField = Field.of("bool_col", StandardSQLTypeName.BOOL);
        FieldList fieldList = FieldList.of(intField, stringField, floatField, boolField);

        FieldValueList row = FieldValueList.of(
                Arrays.asList(
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, 42L),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "test"),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, 3.14),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, true)),
                fieldList);

        FieldValueListMapper mapper = new FieldValueListMapper(fieldList);
        Map<String, Object> result = mapper.map(row);

        assertNotNull(result);
        assertEquals(4, result.size());
        assertEquals(42L, result.get("int_col"));
        assertEquals("test", result.get("string_col"));
        assertEquals(3.14, result.get("float_col"));
        assertEquals(true, result.get("bool_col"));
    }

    @Test
    public void mapEmptyRow() {
        FieldList fieldList = FieldList.of();

        FieldValueList row = FieldValueList.of(List.of(), fieldList);

        FieldValueListMapper mapper = new FieldValueListMapper(fieldList);
        Map<String, Object> result = mapper.map(row);

        assertNotNull(result);
        assertTrue(result.isEmpty(), "Result should be empty map");
    }

    @Test
    public void mapRowWithSpecialCharactersInFieldNames() {
        Field field1 = Field.of("field_with_underscore", StandardSQLTypeName.STRING);
        Field field2 = Field.of("field-with-dash", StandardSQLTypeName.STRING);
        Field field3 = Field.of("fieldWithCamelCase", StandardSQLTypeName.STRING);
        FieldList fieldList = FieldList.of(field1, field2, field3);

        FieldValueList row = FieldValueList.of(
                Arrays.asList(
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "value1"),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "value2"),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "value3")),
                fieldList);

        FieldValueListMapper mapper = new FieldValueListMapper(fieldList);
        Map<String, Object> result = mapper.map(row);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("value1", result.get("field_with_underscore"));
        assertEquals("value2", result.get("field-with-dash"));
        assertEquals("value3", result.get("fieldWithCamelCase"));
    }
}
