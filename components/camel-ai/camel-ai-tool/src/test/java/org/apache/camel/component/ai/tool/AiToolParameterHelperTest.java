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
package org.apache.camel.component.ai.tool;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiToolParameterHelperTest {

    @Test
    public void testSplitTags() {
        assertThat(AiToolParameterHelper.splitTags("a,b,c"))
                .as("Splitting comma-separated tags")
                .containsExactly("a", "b", "c");
        assertThat(AiToolParameterHelper.splitTags("single"))
                .as("Single tag without comma")
                .containsExactly("single");
        assertThat(AiToolParameterHelper.splitTags(" a , b , c "))
                .as("Whitespace-padded tags should be trimmed")
                .containsExactly("a", "b", "c");
    }

    @Test
    public void testSplitTagsNullAndEmpty() {
        assertThat(AiToolParameterHelper.splitTags(null))
                .as("Null input should return empty array")
                .isEmpty();
        assertThat(AiToolParameterHelper.splitTags(""))
                .as("Empty string should return empty array")
                .isEmpty();
        assertThat(AiToolParameterHelper.splitTags("   "))
                .as("Blank string should return empty array")
                .isEmpty();
    }

    @Test
    public void testParseSimpleType() {
        Map<String, String> params = Map.of("city", "string");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(params);

        assertThat(defs)
                .as("Should parse one parameter")
                .hasSize(1);
        assertThat(defs.get("city"))
                .as("City parameter")
                .satisfies(city -> {
                    assertThat(city.getType()).as("Type").isEqualTo("string");
                    assertThat(city.getDescription()).as("Description").isNull();
                    assertThat(city.isRequired()).as("Required").isFalse();
                });
    }

    @Test
    public void testParseWithDescription() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("city", "string");
        params.put("city.description", "The city name");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(params);

        assertThat(defs)
                .as("Should parse one parameter with description")
                .hasSize(1);
        assertThat(defs.get("city"))
                .as("City parameter")
                .satisfies(city -> {
                    assertThat(city.getType()).as("Type").isEqualTo("string");
                    assertThat(city.getDescription()).as("Description").isEqualTo("The city name");
                });
    }

    @Test
    public void testParseWithRequired() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("city", "string");
        params.put("city.required", "true");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(params);

        assertThat(defs.get("city").isRequired())
                .as("City parameter should be required")
                .isTrue();
    }

    @Test
    public void testParseWithEnum() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("unit", "string");
        params.put("unit.enum", "celsius,fahrenheit");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(params);

        assertThat(defs.get("unit").getEnumValues())
                .as("Unit enum values")
                .isNotNull()
                .containsExactly("celsius", "fahrenheit");
    }

    @Test
    public void testParseMultipleParameters() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("city", "string");
        params.put("city.description", "The city name");
        params.put("city.required", "true");
        params.put("unit", "string");
        params.put("unit.enum", "celsius,fahrenheit");
        params.put("days", "integer");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(params);

        assertThat(defs)
                .as("Should parse all three parameters")
                .hasSize(3)
                .containsKey("city")
                .containsKey("unit")
                .containsKey("days");
        assertThat(defs.get("city").getType()).as("City type").isEqualTo("string");
        assertThat(defs.get("days").getType()).as("Days type").isEqualTo("integer");
    }

    @Test
    public void testBuildJsonSchemaBasic() throws DeserializationException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("city", "string");
        params.put("city.description", "The city name");
        params.put("city.required", "true");

        String schema = AiToolParameterHelper.buildJsonSchema(params);
        JsonObject root = (JsonObject) Jsoner.deserialize(schema);

        assertThat(root.getString("type"))
                .as("Schema type")
                .isEqualTo("object");
        JsonObject properties = root.getMap("properties");
        JsonObject city = (JsonObject) properties.get("city");
        assertThat(city)
                .as("City property should exist")
                .isNotNull();
        assertThat(city.getString("type"))
                .as("City property type")
                .isEqualTo("string");
        assertThat(city.getString("description"))
                .as("City property description")
                .isEqualTo("The city name");
        JsonArray required = root.getCollection("required");
        assertThat(required)
                .as("Required array should contain city")
                .contains("city");
        assertThat(root.getBoolean("additionalProperties"))
                .as("additionalProperties should be false")
                .isFalse();
    }

    @Test
    public void testBuildJsonSchemaWithEnum() throws DeserializationException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("unit", "string");
        params.put("unit.enum", "celsius,fahrenheit");

        String schema = AiToolParameterHelper.buildJsonSchema(params);
        JsonObject root = (JsonObject) Jsoner.deserialize(schema);

        JsonObject unitProp = (JsonObject) root.getMap("properties").get("unit");
        JsonArray enumValues = (JsonArray) unitProp.get("enum");
        assertThat(enumValues)
                .as("Unit enum should be present")
                .isNotNull()
                .hasSize(2);
        assertThat(enumValues.get(0))
                .as("First enum value")
                .isEqualTo("celsius");
        assertThat(enumValues.get(1))
                .as("Second enum value")
                .isEqualTo("fahrenheit");
    }

    @Test
    public void testBuildJsonSchemaTypeMapping() throws DeserializationException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("count", "integer");
        params.put("score", "number");
        params.put("active", "boolean");
        params.put("name", "string");

        String schema = AiToolParameterHelper.buildJsonSchema(params);
        JsonObject root = (JsonObject) Jsoner.deserialize(schema);
        JsonObject properties = root.getMap("properties");

        assertThat(((JsonObject) properties.get("count")).getString("type"))
                .as("Integer type mapping").isEqualTo("integer");
        assertThat(((JsonObject) properties.get("score")).getString("type"))
                .as("Number type mapping").isEqualTo("number");
        assertThat(((JsonObject) properties.get("active")).getString("type"))
                .as("Boolean type mapping").isEqualTo("boolean");
        assertThat(((JsonObject) properties.get("name")).getString("type"))
                .as("String type mapping").isEqualTo("string");
    }

    @Test
    public void testBuildJsonSchemaNoRequired() throws DeserializationException {
        Map<String, String> params = Map.of("city", "string");

        String schema = AiToolParameterHelper.buildJsonSchema(params);
        JsonObject root = (JsonObject) Jsoner.deserialize(schema);

        assertThat(root.get("required"))
                .as("Schema should not have a required array when no parameter is required")
                .isNull();
    }

    @Test
    public void testBuildJsonSchemaTypeMappingAliases() throws DeserializationException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("a", "int");
        params.put("b", "long");
        params.put("c", "double");
        params.put("d", "float");
        params.put("e", "bool");

        String schema = AiToolParameterHelper.buildJsonSchema(params);
        JsonObject root = (JsonObject) Jsoner.deserialize(schema);
        JsonObject properties = root.getMap("properties");

        assertThat(((JsonObject) properties.get("a")).getString("type"))
                .as("int alias").isEqualTo("integer");
        assertThat(((JsonObject) properties.get("b")).getString("type"))
                .as("long alias").isEqualTo("integer");
        assertThat(((JsonObject) properties.get("c")).getString("type"))
                .as("double alias").isEqualTo("number");
        assertThat(((JsonObject) properties.get("d")).getString("type"))
                .as("float alias").isEqualTo("number");
        assertThat(((JsonObject) properties.get("e")).getString("type"))
                .as("bool alias").isEqualTo("boolean");
    }

    @Test
    public void testParseDefaultType() {
        Map<String, String> params = Map.of("city.description", "The city name");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(params);

        assertThat(defs.get("city").getType())
                .as("Default type should be string when only description is provided")
                .isEqualTo("string");
    }
}
