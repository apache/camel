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
}
