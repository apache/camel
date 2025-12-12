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
package org.apache.camel.component.springai.tools;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Consumer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonSchemaTest extends CamelTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testJsonSchemaGenerationWithFullMetadata() throws Exception {
        // Create parameters with full metadata
        Map<String, String> parameters = new HashMap<>();
        parameters.put("location", "string");
        parameters.put("location.description", "The city and state e.g. San Francisco, CA");
        parameters.put("location.required", "true");
        parameters.put("unit", "string");
        parameters.put("unit.description", "The temperature unit");
        parameters.put("unit.enum", "C,F");
        parameters.put("unit.required", "true");
        parameters.put("threshold", "number");
        parameters.put("threshold.description", "Temperature threshold");

        // Create endpoint and test schema generation
        SpringAiToolsComponent component = new SpringAiToolsComponent(context);
        SpringAiToolsEndpoint endpoint = new SpringAiToolsEndpoint(
                "spring-ai-tools:test",
                component,
                "test-tool",
                "test-tag",
                new SpringAiToolsConfiguration());

        endpoint.setParameters(parameters);
        endpoint.setDescription("Test tool");

        // Access the private method via reflection for testing
        java.lang.reflect.Method method
                = SpringAiToolsEndpoint.class.getDeclaredMethod("buildJsonSchemaFromParameters", Map.class);
        method.setAccessible(true);
        String schema = (String) method.invoke(endpoint, parameters);

        System.out.println("Generated Schema:");
        System.out.println(schema);

        // Parse and validate the schema
        JsonNode schemaNode = MAPPER.readTree(schema);

        // Verify basic structure
        assertEquals("object", schemaNode.get("type").asText());
        assertNotNull(schemaNode.get("properties"));

        // Verify location property
        JsonNode locationProp = schemaNode.get("properties").get("location");
        assertNotNull(locationProp);
        assertEquals("string", locationProp.get("type").asText());
        assertEquals("The city and state e.g. San Francisco, CA", locationProp.get("description").asText());

        // Verify unit property with enum
        JsonNode unitProp = schemaNode.get("properties").get("unit");
        assertNotNull(unitProp);
        assertEquals("string", unitProp.get("type").asText());
        assertEquals("The temperature unit", unitProp.get("description").asText());
        assertTrue(unitProp.has("enum"));
        assertEquals("C", unitProp.get("enum").get(0).asText());
        assertEquals("F", unitProp.get("enum").get(1).asText());

        // Verify threshold property
        JsonNode thresholdProp = schemaNode.get("properties").get("threshold");
        assertNotNull(thresholdProp);
        assertEquals("number", thresholdProp.get("type").asText());
        assertEquals("Temperature threshold", thresholdProp.get("description").asText());

        // Verify required array
        JsonNode required = schemaNode.get("required");
        assertNotNull(required);
        assertEquals(2, required.size());
        assertTrue(required.toString().contains("location"));
        assertTrue(required.toString().contains("unit"));
    }

    @Test
    public void testJsonSchemaGenerationWithMinimalMetadata() throws Exception {
        // Create parameters with minimal metadata
        Map<String, String> parameters = new HashMap<>();
        parameters.put("name", "string");
        parameters.put("age", "integer");

        // Create endpoint and test schema generation
        SpringAiToolsComponent component = new SpringAiToolsComponent(context);
        SpringAiToolsEndpoint endpoint = new SpringAiToolsEndpoint(
                "spring-ai-tools:test",
                component,
                "test-tool",
                "test-tag",
                new SpringAiToolsConfiguration());

        endpoint.setParameters(parameters);
        endpoint.setDescription("Test tool");

        // Access the private method via reflection for testing
        java.lang.reflect.Method method
                = SpringAiToolsEndpoint.class.getDeclaredMethod("buildJsonSchemaFromParameters", Map.class);
        method.setAccessible(true);
        String schema = (String) method.invoke(endpoint, parameters);

        System.out.println("Generated Minimal Schema:");
        System.out.println(schema);

        // Parse and validate the schema
        JsonNode schemaNode = MAPPER.readTree(schema);

        // Verify basic structure
        assertEquals("object", schemaNode.get("type").asText());
        assertNotNull(schemaNode.get("properties"));

        // Verify properties
        JsonNode nameProp = schemaNode.get("properties").get("name");
        assertNotNull(nameProp);
        assertEquals("string", nameProp.get("type").asText());

        JsonNode ageProp = schemaNode.get("properties").get("age");
        assertNotNull(ageProp);
        assertEquals("integer", ageProp.get("type").asText());

        // Verify no required array (since no required=true was specified)
        // In JSON, if required is empty, it might not be present or be an empty array
        assertTrue(schemaNode.get("required") == null || schemaNode.get("required").size() == 0);
    }

    @Test
    public void testToolMetadataReturnDirectConfiguration() throws Exception {
        // Create endpoint with returnDirect=true
        SpringAiToolsComponent component = new SpringAiToolsComponent(context);
        SpringAiToolsEndpoint endpoint = new SpringAiToolsEndpoint(
                "spring-ai-tools:directTool?returnDirect=true",
                component,
                "directTool",
                "test-tag",
                new SpringAiToolsConfiguration());

        endpoint.setDescription("Tool that returns directly");
        endpoint.setReturnDirect(true);

        // Create a simple consumer to test
        Consumer consumer = endpoint.createConsumer(exchange -> {
            exchange.getIn().setBody("Direct result");
        });

        assertNotNull(consumer);

        // Verify the returnDirect property is set
        assertTrue(endpoint.isReturnDirect());
    }

    @Test
    public void testToolMetadataDefaultReturnDirect() throws Exception {
        // Create endpoint without returnDirect (should default to false)
        SpringAiToolsComponent component = new SpringAiToolsComponent(context);
        SpringAiToolsEndpoint endpoint = new SpringAiToolsEndpoint(
                "spring-ai-tools:normalTool",
                component,
                "normalTool",
                "test-tag",
                new SpringAiToolsConfiguration());

        endpoint.setDescription("Normal tool");

        // Create a simple consumer to test
        Consumer consumer = endpoint.createConsumer(exchange -> {
            exchange.getIn().setBody("Normal result");
        });

        assertNotNull(consumer);

        // Verify the returnDirect property defaults to false
        assertTrue(!endpoint.isReturnDirect());
    }
}
