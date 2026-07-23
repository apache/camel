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
package org.apache.camel.component.langchain4j.agent;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.apache.camel.component.ai.tool.AiToolParameterHelper;
import org.apache.camel.component.ai.tool.AiToolSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiToolSpecToLangChain4jTest {

    @Test
    void testBasicToolSpecConversion() {
        AiToolSpec spec = new AiToolSpec("myTool", "A test tool", Map.of(), null, null);

        ToolSpecification result = AiToolSpecToLangChain4j.toToolSpecification(spec);

        assertEquals("myTool", result.name());
        assertEquals("A test tool", result.description());
        assertNull(result.parameters());
    }

    @Test
    void testStringParameter() {
        Map<String, String> rawParams = new LinkedHashMap<>();
        rawParams.put("city", "string");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(rawParams);
        AiToolSpec spec = new AiToolSpec("weather", "Get weather", defs, null, null);

        ToolSpecification result = AiToolSpecToLangChain4j.toToolSpecification(spec);

        assertNotNull(result.parameters());
        JsonObjectSchema schema = result.parameters();
        assertInstanceOf(JsonStringSchema.class, schema.properties().get("city"));
    }

    @Test
    void testIntegerParameter() {
        Map<String, String> rawParams = new LinkedHashMap<>();
        rawParams.put("count", "integer");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(rawParams);
        AiToolSpec spec = new AiToolSpec("counter", "Count items", defs, null, null);

        ToolSpecification result = AiToolSpecToLangChain4j.toToolSpecification(spec);

        assertInstanceOf(JsonIntegerSchema.class, result.parameters().properties().get("count"));
    }

    @Test
    void testNumberParameter() {
        Map<String, String> rawParams = new LinkedHashMap<>();
        rawParams.put("price", "number");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(rawParams);
        AiToolSpec spec = new AiToolSpec("pricer", "Get price", defs, null, null);

        ToolSpecification result = AiToolSpecToLangChain4j.toToolSpecification(spec);

        assertInstanceOf(JsonNumberSchema.class, result.parameters().properties().get("price"));
    }

    @Test
    void testBooleanParameter() {
        Map<String, String> rawParams = new LinkedHashMap<>();
        rawParams.put("active", "boolean");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(rawParams);
        AiToolSpec spec = new AiToolSpec("checker", "Check status", defs, null, null);

        ToolSpecification result = AiToolSpecToLangChain4j.toToolSpecification(spec);

        assertInstanceOf(JsonBooleanSchema.class, result.parameters().properties().get("active"));
    }

    @Test
    void testEnumParameter() {
        Map<String, String> rawParams = new LinkedHashMap<>();
        rawParams.put("color", "string");
        rawParams.put("color.enum", "red,green,blue");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(rawParams);
        AiToolSpec spec = new AiToolSpec("colorPicker", "Pick a color", defs, null, null);

        ToolSpecification result = AiToolSpecToLangChain4j.toToolSpecification(spec);

        JsonEnumSchema enumSchema = assertInstanceOf(JsonEnumSchema.class, result.parameters().properties().get("color"));
        assertEquals(3, enumSchema.enumValues().size());
        assertTrue(enumSchema.enumValues().contains("red"));
        assertTrue(enumSchema.enumValues().contains("green"));
        assertTrue(enumSchema.enumValues().contains("blue"));
    }

    @Test
    void testRequiredParameter() {
        Map<String, String> rawParams = new LinkedHashMap<>();
        rawParams.put("userId", "string");
        rawParams.put("userId.required", "true");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(rawParams);
        AiToolSpec spec = new AiToolSpec("userLookup", "Look up user", defs, null, null);

        ToolSpecification result = AiToolSpecToLangChain4j.toToolSpecification(spec);

        assertNotNull(result.parameters().required());
        assertTrue(result.parameters().required().contains("userId"));
    }

    @Test
    void testMultipleParametersWithMixedTypes() {
        Map<String, String> rawParams = new LinkedHashMap<>();
        rawParams.put("name", "string");
        rawParams.put("name.required", "true");
        rawParams.put("age", "integer");
        rawParams.put("score", "number");
        rawParams.put("active", "boolean");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(rawParams);
        AiToolSpec spec = new AiToolSpec("multiTool", "Multi-param tool", defs, null, null);

        ToolSpecification result = AiToolSpecToLangChain4j.toToolSpecification(spec);

        assertEquals(4, result.parameters().properties().size());
        assertInstanceOf(JsonStringSchema.class, result.parameters().properties().get("name"));
        assertInstanceOf(JsonIntegerSchema.class, result.parameters().properties().get("age"));
        assertInstanceOf(JsonNumberSchema.class, result.parameters().properties().get("score"));
        assertInstanceOf(JsonBooleanSchema.class, result.parameters().properties().get("active"));

        assertTrue(result.parameters().required().contains("name"));
        assertEquals(1, result.parameters().required().size());
    }

    @Test
    void testEmptyParameters() {
        AiToolSpec spec = new AiToolSpec("noParams", "Tool with no params", Map.of(), null, null);

        ToolSpecification result = AiToolSpecToLangChain4j.toToolSpecification(spec);

        assertNull(result.parameters());
    }
}
