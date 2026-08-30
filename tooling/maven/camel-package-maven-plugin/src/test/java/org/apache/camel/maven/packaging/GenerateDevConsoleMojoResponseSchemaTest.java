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
package org.apache.camel.maven.packaging;

import java.util.List;
import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GenerateDevConsoleMojoResponseSchemaTest {

    static class NoResponseConsole {
    }

    static class SampleConsole {

        record Row(String id, long value) {
        }

        public record Response(
                @Metadata(description = "The size") int size,
                @Metadata(description = "An optional label") String label,
                Row row,
                List<Row> rows,
                Map<String, String> details,
                Map<String, Object> opaque,
                Object anyValue) {
        }
    }

    static class GenericFieldConsole<T> {

        // a type parameter has no resolvable Class, exercising the TypeVariable fallback
        public record Response<T>(T value) {
        }
    }

    @Test
    void consoleWithoutResponseRecordReturnsNull() {
        JsonObject schema = GenerateDevConsoleMojo.buildResponseSchema(NoResponseConsole.class);
        assertEquals(null, schema);
    }

    @Test
    void primitiveFieldIsRequiredAndDescribed() {
        JsonObject schema = GenerateDevConsoleMojo.buildResponseSchema(SampleConsole.class);

        assertEquals("object", schema.getString("type"));
        JsonObject properties = schema.getJsonObject("properties");

        JsonObject size = properties.getJsonObject("size");
        assertEquals("integer", size.getString("type"));
        assertEquals("The size", size.getString("description"));

        var required = schema.getCollection("required");
        assertTrue(required.contains("size"));
    }

    @Test
    void referenceFieldIsNotRequired() {
        JsonObject schema = GenerateDevConsoleMojo.buildResponseSchema(SampleConsole.class);
        JsonObject properties = schema.getJsonObject("properties");

        JsonObject label = properties.getJsonObject("label");
        assertEquals("string", label.getString("type"));
        assertEquals("An optional label", label.getString("description"));

        var required = schema.getCollection("required");
        assertFalse(required.contains("label"));
    }

    @Test
    void nestedRecordBuildsNestedObjectSchema() {
        JsonObject schema = GenerateDevConsoleMojo.buildResponseSchema(SampleConsole.class);
        JsonObject properties = schema.getJsonObject("properties");

        JsonObject row = properties.getJsonObject("row");
        assertEquals("object", row.getString("type"));
        JsonObject rowProperties = row.getJsonObject("properties");
        assertEquals("string", rowProperties.getJsonObject("id").getString("type"));
        assertEquals("integer", rowProperties.getJsonObject("value").getString("type"));
    }

    @Test
    void listOfRecordBuildsArrayOfObjectSchema() {
        JsonObject schema = GenerateDevConsoleMojo.buildResponseSchema(SampleConsole.class);
        JsonObject properties = schema.getJsonObject("properties");

        JsonObject rows = properties.getJsonObject("rows");
        assertEquals("array", rows.getString("type"));
        JsonObject items = rows.getJsonObject("items");
        assertEquals("object", items.getString("type"));
        assertEquals("string", items.getJsonObject("properties").getJsonObject("id").getString("type"));
    }

    @Test
    void mapWithTypedValueBuildsAdditionalPropertiesSchema() {
        JsonObject schema = GenerateDevConsoleMojo.buildResponseSchema(SampleConsole.class);
        JsonObject properties = schema.getJsonObject("properties");

        JsonObject details = properties.getJsonObject("details");
        assertEquals("object", details.getString("type"));
        assertEquals("string", details.getJsonObject("additionalProperties").getString("type"));
        assertNull(details.get("properties"));
    }

    @Test
    void mapWithObjectValueBuildsFullyOpenSchema() {
        JsonObject schema = GenerateDevConsoleMojo.buildResponseSchema(SampleConsole.class);
        JsonObject properties = schema.getJsonObject("properties");

        JsonObject opaque = properties.getJsonObject("opaque");
        assertEquals("object", opaque.getString("type"));
        assertEquals(true, opaque.get("additionalProperties"));
    }

    @Test
    void bareObjectFieldBuildsUnconstrainedSchema() {
        JsonObject schema = GenerateDevConsoleMojo.buildResponseSchema(SampleConsole.class);
        JsonObject properties = schema.getJsonObject("properties");

        JsonObject anyValue = properties.getJsonObject("anyValue");
        assertTrue(anyValue.isEmpty());
    }

    @Test
    void typeVariableFieldBuildsUnconstrainedSchemaInsteadOfThrowing() {
        JsonObject schema = GenerateDevConsoleMojo.buildResponseSchema(GenericFieldConsole.class);
        JsonObject properties = schema.getJsonObject("properties");

        JsonObject value = properties.getJsonObject("value");
        assertTrue(value.isEmpty());
    }
}
