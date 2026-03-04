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
package org.apache.camel.component.langchain4j.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.apache.camel.Consumer;
import org.apache.camel.component.langchain4j.tools.spec.CamelSimpleToolParameter;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolExecutorCache;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolSpecification;
import org.apache.camel.component.langchain4j.tools.spec.NamedJsonSchemaProperty;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LangChain4jToolRequiredParameterTest extends CamelTestSupport {

    @AfterEach
    void clearCache() {
        CamelToolExecutorCache.getInstance().getTools().clear();
    }

    @Test
    public void testUriParameterWithRequired() throws Exception {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("location", "string");
        parameters.put("location.description", "The city and state");
        parameters.put("location.required", "true");
        parameters.put("unit", "string");
        parameters.put("unit.required", "false");
        parameters.put("count", "integer");

        LangChain4jToolsComponent component
                = context.getComponent(LangChain4jTools.SCHEME, LangChain4jToolsComponent.class);

        LangChain4jToolsEndpoint endpoint = new LangChain4jToolsEndpoint(
                "langchain4j-tools:test",
                component,
                "test-tool",
                "test-tag",
                new LangChain4jToolsConfiguration());

        endpoint.setCamelContext(context);
        endpoint.setParameters(parameters);
        endpoint.setDescription("Test tool with required params");

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertNotNull(consumer);

        // Retrieve the tool specification from the cache
        Set<CamelToolSpecification> tools = CamelToolExecutorCache.getInstance().getTools().get("test-tag");
        assertNotNull(tools);
        assertEquals(1, tools.size());

        JsonObjectSchema schema = (JsonObjectSchema) tools.iterator().next().getToolSpecification().parameters();
        assertNotNull(schema);

        // Verify properties exist
        assertNotNull(schema.properties().get("location"));
        assertNotNull(schema.properties().get("unit"));
        assertNotNull(schema.properties().get("count"));

        // Verify required list contains only "location"
        assertNotNull(schema.required());
        assertEquals(1, schema.required().size());
        assertTrue(schema.required().contains("location"));

        // Verify description is set on location
        assertEquals("The city and state", schema.properties().get("location").description());
    }

    @Test
    public void testUriParameterWithNoRequired() throws Exception {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("name", "string");
        parameters.put("age", "integer");

        LangChain4jToolsComponent component
                = context.getComponent(LangChain4jTools.SCHEME, LangChain4jToolsComponent.class);

        LangChain4jToolsEndpoint endpoint = new LangChain4jToolsEndpoint(
                "langchain4j-tools:test",
                component,
                "test-tool",
                "test-tag",
                new LangChain4jToolsConfiguration());

        endpoint.setCamelContext(context);
        endpoint.setParameters(parameters);
        endpoint.setDescription("Test tool without required params");

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertNotNull(consumer);

        Set<CamelToolSpecification> tools = CamelToolExecutorCache.getInstance().getTools().get("test-tag");
        assertNotNull(tools);

        JsonObjectSchema schema = (JsonObjectSchema) tools.iterator().next().getToolSpecification().parameters();
        assertNotNull(schema);
        assertNotNull(schema.properties().get("name"));
        assertNotNull(schema.properties().get("age"));

        // No required parameters should be set
        assertTrue(schema.required() == null || schema.required().isEmpty());
    }

    @Test
    public void testProgrammaticWithRequired() throws Exception {
        CamelSimpleToolParameter toolParameter = new CamelSimpleToolParameter(
                "Query user by location and name",
                List.of(
                        new NamedJsonSchemaProperty(
                                "location",
                                JsonStringSchema.builder().description("The city").build()),
                        new NamedJsonSchemaProperty(
                                "name",
                                JsonStringSchema.builder().build()),
                        new NamedJsonSchemaProperty(
                                "age",
                                JsonIntegerSchema.builder().build())),
                List.of("location", "name"));

        LangChain4jToolsComponent component
                = context.getComponent(LangChain4jTools.SCHEME, LangChain4jToolsComponent.class);

        LangChain4jToolsEndpoint endpoint = new LangChain4jToolsEndpoint(
                "langchain4j-tools:test",
                component,
                "test-tool",
                "test-tag",
                new LangChain4jToolsConfiguration());

        endpoint.setCamelContext(context);
        endpoint.setCamelToolParameter(toolParameter);

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertNotNull(consumer);

        Set<CamelToolSpecification> tools = CamelToolExecutorCache.getInstance().getTools().get("test-tag");
        assertNotNull(tools);

        JsonObjectSchema schema = (JsonObjectSchema) tools.iterator().next().getToolSpecification().parameters();
        assertNotNull(schema);

        // Verify required list
        assertNotNull(schema.required());
        assertEquals(2, schema.required().size());
        assertTrue(schema.required().contains("location"));
        assertTrue(schema.required().contains("name"));

        // Verify description is preserved
        assertEquals("The city", schema.properties().get("location").description());
    }

    @Test
    public void testProgrammaticWithoutRequired() throws Exception {
        CamelSimpleToolParameter toolParameter = new CamelSimpleToolParameter(
                "Simple tool",
                List.of(
                        new NamedJsonSchemaProperty("name", JsonStringSchema.builder().build())));

        LangChain4jToolsComponent component
                = context.getComponent(LangChain4jTools.SCHEME, LangChain4jToolsComponent.class);

        LangChain4jToolsEndpoint endpoint = new LangChain4jToolsEndpoint(
                "langchain4j-tools:test",
                component,
                "test-tool",
                "test-tag",
                new LangChain4jToolsConfiguration());

        endpoint.setCamelContext(context);
        endpoint.setCamelToolParameter(toolParameter);

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertNotNull(consumer);

        Set<CamelToolSpecification> tools = CamelToolExecutorCache.getInstance().getTools().get("test-tag");
        assertNotNull(tools);

        JsonObjectSchema schema = (JsonObjectSchema) tools.iterator().next().getToolSpecification().parameters();
        assertNotNull(schema);

        // No required parameters
        assertTrue(schema.required() == null || schema.required().isEmpty());
    }

    @Test
    public void testUriParameterWithDescription() throws Exception {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("city", "string");
        parameters.put("city.description", "The name of the city");
        parameters.put("temperature", "number");
        parameters.put("temperature.description", "Temperature threshold");

        LangChain4jToolsComponent component
                = context.getComponent(LangChain4jTools.SCHEME, LangChain4jToolsComponent.class);

        LangChain4jToolsEndpoint endpoint = new LangChain4jToolsEndpoint(
                "langchain4j-tools:test",
                component,
                "test-tool",
                "test-tag",
                new LangChain4jToolsConfiguration());

        endpoint.setCamelContext(context);
        endpoint.setParameters(parameters);
        endpoint.setDescription("Weather tool");

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertNotNull(consumer);

        Set<CamelToolSpecification> tools = CamelToolExecutorCache.getInstance().getTools().get("test-tag");
        assertNotNull(tools);

        JsonObjectSchema schema = (JsonObjectSchema) tools.iterator().next().getToolSpecification().parameters();
        assertNotNull(schema);

        assertEquals("The name of the city", schema.properties().get("city").description());
        assertEquals("Temperature threshold", schema.properties().get("temperature").description());
    }

    @Test
    public void testUriParameterWithEnum() throws Exception {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("unit", "string");
        parameters.put("unit.enum", "celsius,fahrenheit");

        LangChain4jToolsComponent component
                = context.getComponent(LangChain4jTools.SCHEME, LangChain4jToolsComponent.class);

        LangChain4jToolsEndpoint endpoint = new LangChain4jToolsEndpoint(
                "langchain4j-tools:test",
                component,
                "test-tool",
                "test-tag",
                new LangChain4jToolsConfiguration());

        endpoint.setCamelContext(context);
        endpoint.setParameters(parameters);
        endpoint.setDescription("Tool with enum param");

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertNotNull(consumer);

        Set<CamelToolSpecification> tools = CamelToolExecutorCache.getInstance().getTools().get("test-tag");
        assertNotNull(tools);

        JsonObjectSchema schema = (JsonObjectSchema) tools.iterator().next().getToolSpecification().parameters();
        assertNotNull(schema);

        // Verify the parameter is a JsonEnumSchema
        assertTrue(schema.properties().get("unit") instanceof JsonEnumSchema);
        JsonEnumSchema enumSchema = (JsonEnumSchema) schema.properties().get("unit");
        assertEquals(2, enumSchema.enumValues().size());
        assertTrue(enumSchema.enumValues().contains("celsius"));
        assertTrue(enumSchema.enumValues().contains("fahrenheit"));
    }

    @Test
    public void testUriParameterWithEnumAndDescription() throws Exception {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("unit", "string");
        parameters.put("unit.enum", "C,F");
        parameters.put("unit.description", "Temperature unit");

        LangChain4jToolsComponent component
                = context.getComponent(LangChain4jTools.SCHEME, LangChain4jToolsComponent.class);

        LangChain4jToolsEndpoint endpoint = new LangChain4jToolsEndpoint(
                "langchain4j-tools:test",
                component,
                "test-tool",
                "test-tag",
                new LangChain4jToolsConfiguration());

        endpoint.setCamelContext(context);
        endpoint.setParameters(parameters);
        endpoint.setDescription("Tool with enum and description");

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertNotNull(consumer);

        Set<CamelToolSpecification> tools = CamelToolExecutorCache.getInstance().getTools().get("test-tag");
        assertNotNull(tools);

        JsonObjectSchema schema = (JsonObjectSchema) tools.iterator().next().getToolSpecification().parameters();
        assertNotNull(schema);

        assertTrue(schema.properties().get("unit") instanceof JsonEnumSchema);
        JsonEnumSchema enumSchema = (JsonEnumSchema) schema.properties().get("unit");
        assertEquals(2, enumSchema.enumValues().size());
        assertTrue(enumSchema.enumValues().contains("C"));
        assertTrue(enumSchema.enumValues().contains("F"));
        assertEquals("Temperature unit", enumSchema.description());
    }

    @Test
    public void testProgrammaticWithAdditionalProperties() throws Exception {
        CamelSimpleToolParameter toolParameter = new CamelSimpleToolParameter(
                "Strict tool",
                List.of(
                        new NamedJsonSchemaProperty("name", JsonStringSchema.builder().build())),
                List.of("name"),
                false,
                null);

        LangChain4jToolsComponent component
                = context.getComponent(LangChain4jTools.SCHEME, LangChain4jToolsComponent.class);

        LangChain4jToolsEndpoint endpoint = new LangChain4jToolsEndpoint(
                "langchain4j-tools:test",
                component,
                "test-tool",
                "test-tag",
                new LangChain4jToolsConfiguration());

        endpoint.setCamelContext(context);
        endpoint.setCamelToolParameter(toolParameter);

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertNotNull(consumer);

        Set<CamelToolSpecification> tools = CamelToolExecutorCache.getInstance().getTools().get("test-tag");
        assertNotNull(tools);

        JsonObjectSchema schema = (JsonObjectSchema) tools.iterator().next().getToolSpecification().parameters();
        assertNotNull(schema);
        assertEquals(false, schema.additionalProperties());
    }

    @Test
    public void testProgrammaticWithDefinitions() throws Exception {
        // Create a recursive schema: a "node" with a name and optional children (list of node references)
        Map<String, JsonSchemaElement> definitions = Map.of(
                "node", JsonObjectSchema.builder()
                        .addStringProperty("name")
                        .addProperty("parent", JsonReferenceSchema.builder().reference("node").build())
                        .build());

        CamelSimpleToolParameter toolParameter = new CamelSimpleToolParameter(
                "Tool with recursive schema",
                List.of(
                        new NamedJsonSchemaProperty(
                                "root",
                                JsonReferenceSchema.builder().reference("node").build())),
                List.of("root"),
                null,
                definitions);

        LangChain4jToolsComponent component
                = context.getComponent(LangChain4jTools.SCHEME, LangChain4jToolsComponent.class);

        LangChain4jToolsEndpoint endpoint = new LangChain4jToolsEndpoint(
                "langchain4j-tools:test",
                component,
                "test-tool",
                "test-tag",
                new LangChain4jToolsConfiguration());

        endpoint.setCamelContext(context);
        endpoint.setCamelToolParameter(toolParameter);

        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        assertNotNull(consumer);

        Set<CamelToolSpecification> tools = CamelToolExecutorCache.getInstance().getTools().get("test-tag");
        assertNotNull(tools);

        JsonObjectSchema schema = (JsonObjectSchema) tools.iterator().next().getToolSpecification().parameters();
        assertNotNull(schema);

        // Verify definitions are set
        assertNotNull(schema.definitions());
        assertEquals(1, schema.definitions().size());
        assertTrue(schema.definitions().containsKey("node"));

        // Verify the root property is a reference
        assertTrue(schema.properties().get("root") instanceof JsonReferenceSchema);
    }
}
