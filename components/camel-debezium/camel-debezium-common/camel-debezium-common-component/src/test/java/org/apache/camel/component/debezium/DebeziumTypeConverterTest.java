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

import java.util.List;
import java.util.Map;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DebeziumTypeConverterTest {

    @Test
    void testToMapFromStruct() {
        final Struct inputValue = createTestStruct(12, "test-name", true);

        // convert toMap
        final Map<String, Object> outputValue = DebeziumTypeConverter.toMap(inputValue);

        // assert
        assertNotNull(outputValue);
        assertEquals(12, outputValue.get("id"));
        assertEquals("test-name", outputValue.get("name"));
        assertNull(outputValue.get("extra"));
        assertTrue((boolean) outputValue.get("valid"));
    }

    @Test
    void testToMapNestedStruct() {
        Schema detailsSchema = SchemaBuilder.struct().field("age", SchemaBuilder.INT32_SCHEMA).build();

        Schema valueSchema = SchemaBuilder.struct()
                .name("valueSchema")
                .field("id", SchemaBuilder.INT32_SCHEMA)
                .field("name", SchemaBuilder.STRING_SCHEMA)
                .field("isAdult", SchemaBuilder.BOOLEAN_SCHEMA)
                .field("details", detailsSchema)
                .build();

        Struct inputValue = new Struct(valueSchema)
                .put("id", 12)
                .put("name", "jane doe")
                .put("isAdult", true)
                .put("details", new Struct(detailsSchema).put("age", 30));

        final Map<String, Object> outputValue = DebeziumTypeConverter.toMap(inputValue);

        assertEquals("jane doe", outputValue.get("name"));
        assertEquals(12, outputValue.get("id"));
        assertTrue((Boolean) outputValue.get("isAdult"));
        assertEquals(30, ((Map) outputValue.get("details")).get("age"));
    }

    @Test
    void testToMapStructsInListAndMap() {
        Schema itemSchema = SchemaBuilder.struct().field("sku", SchemaBuilder.STRING_SCHEMA).build();

        Schema valueSchema = SchemaBuilder.struct()
                .field("items", SchemaBuilder.array(itemSchema).build())
                .field("byName", SchemaBuilder.map(SchemaBuilder.STRING_SCHEMA, itemSchema).build())
                .field("tags", SchemaBuilder.array(SchemaBuilder.STRING_SCHEMA).build())
                .build();

        Struct inputValue = new Struct(valueSchema)
                .put("items", List.of(new Struct(itemSchema).put("sku", "a1"), new Struct(itemSchema).put("sku", "b2")))
                .put("byName", Map.of("first", new Struct(itemSchema).put("sku", "c3")))
                .put("tags", List.of("x", "y"));

        final Map<String, Object> outputValue = DebeziumTypeConverter.toMap(inputValue);

        final List<?> items = (List<?>) outputValue.get("items");
        assertEquals(2, items.size());
        assertEquals("a1", ((Map<?, ?>) items.get(0)).get("sku"));
        assertEquals("b2", ((Map<?, ?>) items.get(1)).get("sku"));
        final Map<?, ?> byName = (Map<?, ?>) outputValue.get("byName");
        assertEquals("c3", ((Map<?, ?>) byName.get("first")).get("sku"));
        assertEquals(List.of("x", "y"), outputValue.get("tags"));
    }

    private Struct createTestStruct(final int id, final String name, final boolean valid) {
        final Schema schema = SchemaBuilder.struct()
                .field("id", Schema.INT32_SCHEMA)
                .field("name", Schema.STRING_SCHEMA)
                .field("valid", Schema.BOOLEAN_SCHEMA)
                .field("extra", Schema.STRING_SCHEMA)
                .build();

        final Struct value = new Struct(schema);
        value.put("id", id);
        value.put("name", name);
        value.put("valid", valid);

        return value;
    }

    @Test
    void testToMapKeepsTheOrderOfTheFields() {
        // the order of the columns of the table, which a HashMap would lose
        Schema detailsSchema = SchemaBuilder.struct()
                .field("zip", SchemaBuilder.STRING_SCHEMA)
                .field("city", SchemaBuilder.STRING_SCHEMA)
                .field("country", SchemaBuilder.STRING_SCHEMA)
                .build();
        Schema valueSchema = SchemaBuilder.struct()
                .field("id", SchemaBuilder.INT32_SCHEMA)
                .field("name", SchemaBuilder.STRING_SCHEMA)
                .field("price", SchemaBuilder.FLOAT64_SCHEMA)
                .field("added", SchemaBuilder.STRING_SCHEMA)
                .field("address", detailsSchema)
                .field("lines", SchemaBuilder.array(detailsSchema).build())
                .build();
        Struct address = new Struct(detailsSchema).put("zip", "8000").put("city", "Aarhus").put("country", "DK");
        Struct inputValue = new Struct(valueSchema)
                .put("id", 1).put("name", "camel").put("price", 9.95).put("added", "2026-11-22")
                .put("address", address).put("lines", List.of(address));

        Map<String, Object> outputValue = DebeziumTypeConverter.toMap(inputValue);

        assertEquals(List.of("id", "name", "price", "added", "address", "lines"), List.copyOf(outputValue.keySet()));
        assertEquals(List.of("zip", "city", "country"), List.copyOf(((Map<?, ?>) outputValue.get("address")).keySet()));
        Map<?, ?> line = (Map<?, ?>) ((List<?>) outputValue.get("lines")).get(0);
        assertEquals(List.of("zip", "city", "country"), List.copyOf(line.keySet()));
    }
}
