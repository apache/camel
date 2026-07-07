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
package org.apache.camel.component.langchain4j.agent.api;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AgentConfigurationTest {

    @Test
    public void testParseGuardrailClasses_WithValidClasses() {
        // Test with built-in Java classes that should always be available
        String classNames = "java.lang.String,java.util.List,java.io.Serializable";

        List<Class<?>> result = AgentConfiguration.parseGuardrailClasses(classNames);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(String.class));
        assertTrue(result.contains(List.class));
        assertTrue(result.contains(Serializable.class));
    }

    @Test
    public void testParseGuardrailClasses_WithMixedValidAndInvalidClasses() {
        // Mix of valid and invalid class names
        String classNames = "java.lang.String,com.nonexistent.InvalidClass,java.util.List";

        List<Class<?>> result = AgentConfiguration.parseGuardrailClasses(classNames);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(String.class));
        assertTrue(result.contains(List.class));
    }

    @Test
    public void testParseGuardrailClasses_WithInvalidClasses() {
        // Test with non-existent classes
        String classNames = "com.nonexistent.InvalidClass1,com.nonexistent.InvalidClass2";

        List<Class<?>> result = AgentConfiguration.parseGuardrailClasses(classNames);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseGuardrailClasses_WithNullInput() {
        List<Class<?>> result = AgentConfiguration.parseGuardrailClasses((String) null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseGuardrailClasses_WithEmptyString() {
        List<Class<?>> result = AgentConfiguration.parseGuardrailClasses("");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseGuardrailClasses_WithWhitespaceOnly() {
        List<Class<?>> result = AgentConfiguration.parseGuardrailClasses("   ");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseGuardrailClasses_WithExtraWhitespace() {
        // Test with extra whitespace around class names
        String classNames = " java.lang.String , java.util.List , java.io.Serializable ";

        List<Class<?>> result = AgentConfiguration.parseGuardrailClasses(classNames);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(String.class));
        assertTrue(result.contains(List.class));
        assertTrue(result.contains(Serializable.class));
    }

    @Test
    public void testParseGuardrailClasses_WithEmptyClassNames() {
        // Test with empty class names (multiple commas)
        String classNames = "java.lang.String,,java.util.List,,,java.io.Serializable,";

        List<Class<?>> result = AgentConfiguration.parseGuardrailClasses(classNames);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(String.class));
        assertTrue(result.contains(List.class));
        assertTrue(result.contains(Serializable.class));
    }

    @Test
    public void testParseGuardrailClasses_WithSingleClass() {
        String classNames = "java.lang.String";

        List<Class<?>> result = AgentConfiguration.parseGuardrailClasses(classNames);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.contains(String.class));
    }

    @Test
    public void testParseGuardrailClasses_WithTrailingComma() {
        String classNames = "java.lang.String,java.util.List,";

        List<Class<?>> result = AgentConfiguration.parseGuardrailClasses(classNames);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(String.class));
        assertTrue(result.contains(List.class));
    }

    // Tests for array-based parseGuardrailClasses method

    @Test
    public void testParseGuardrailClasses_WithValidClassesArray() {
        String[] classNames = { "java.lang.String", "java.util.List", "java.io.Serializable" };

        List<Class<?>> result = AgentConfiguration.parseGuardrailClasses(classNames);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(String.class));
        assertTrue(result.contains(List.class));
        assertTrue(result.contains(Serializable.class));
    }

    @Test
    public void testParseGuardrailClasses_WithMixedValidAndInvalidClassesArray() {
        String[] classNames = { "java.lang.String", "com.nonexistent.InvalidClass", "java.util.List" };

        List<Class<?>> result = AgentConfiguration.parseGuardrailClasses(classNames);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(String.class));
        assertTrue(result.contains(List.class));
    }

    @Test
    public void testParseGuardrailClasses_WithNullArray() {
        String[] classNames = null;

        List<Class<?>> result = AgentConfiguration.parseGuardrailClasses(classNames);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseGuardrailClasses_WithEmptyArray() {
        String[] classNames = {};

        List<Class<?>> result = AgentConfiguration.parseGuardrailClasses(classNames);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseGuardrailClasses_WithArrayContainingEmptyStrings() {
        String[] classNames = { "java.lang.String", "", "java.util.List", "   ", "java.io.Serializable" };

        List<Class<?>> result = AgentConfiguration.parseGuardrailClasses(classNames);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(String.class));
        assertTrue(result.contains(List.class));
        assertTrue(result.contains(Serializable.class));
    }

    // Tests for fluent methods with arrays

    @Test
    public void testWithInputGuardrailClassesArray() {
        String[] classNames = { "java.lang.String", "java.util.List" };

        AgentConfiguration config = new AgentConfiguration()
                .withInputGuardrailClassesArray(classNames);

        assertNotNull(config.getInputGuardrailClasses());
        assertEquals(2, config.getInputGuardrailClasses().size());
        assertTrue(config.getInputGuardrailClasses().contains(String.class));
        assertTrue(config.getInputGuardrailClasses().contains(List.class));
    }

    @Test
    public void testWithOutputGuardrailClassesArray() {
        String[] classNames = { "java.lang.String", "java.util.List" };

        AgentConfiguration config = new AgentConfiguration()
                .withOutputGuardrailClassesArray(classNames);

        assertNotNull(config.getOutputGuardrailClasses());
        assertEquals(2, config.getOutputGuardrailClasses().size());
        assertTrue(config.getOutputGuardrailClasses().contains(String.class));
        assertTrue(config.getOutputGuardrailClasses().contains(List.class));
    }

    @Test
    public void testWithInputGuardrailClassesArray_WithNullArray() {
        String[] classNames = null;

        AgentConfiguration config = new AgentConfiguration()
                .withInputGuardrailClassesArray(classNames);

        assertNotNull(config.getInputGuardrailClasses());
        assertTrue(config.getInputGuardrailClasses().isEmpty());
    }

    @Test
    public void testWithOutputGuardrailClassesArray_WithInvalidClasses() {
        String[] classNames = { "com.nonexistent.InvalidClass1", "com.nonexistent.InvalidClass2" };

        AgentConfiguration config = new AgentConfiguration()
                .withOutputGuardrailClassesArray(classNames);

        assertNotNull(config.getOutputGuardrailClasses());
        assertTrue(config.getOutputGuardrailClasses().isEmpty());
    }

    // Tests for tool-calling options

    @Test
    public void testMaxToolCallingRoundTrips() {
        AgentConfiguration config = new AgentConfiguration();
        assertEquals(0, config.getMaxToolCallingRoundTrips());

        AgentConfiguration result = config.withMaxToolCallingRoundTrips(10);

        assertSame(config, result);
        assertEquals(10, config.getMaxToolCallingRoundTrips());
    }

    @Test
    public void testHallucinatedToolNameStrategy() {
        AgentConfiguration config = new AgentConfiguration();
        assertNull(config.getHallucinatedToolNameStrategy());

        Function<ToolExecutionRequest, ToolExecutionResultMessage> strategy
                = request -> ToolExecutionResultMessage.from(request, "Unknown tool: " + request.name());
        AgentConfiguration result = config.withHallucinatedToolNameStrategy(strategy);

        assertSame(config, result);
        assertSame(strategy, config.getHallucinatedToolNameStrategy());
    }

    @Test
    public void testToolExecutionErrorHandler() {
        AgentConfiguration config = new AgentConfiguration();
        assertNull(config.getToolExecutionErrorHandler());

        ToolExecutionErrorHandler handler = (error, context) -> null;
        AgentConfiguration result = config.withToolExecutionErrorHandler(handler);

        assertSame(config, result);
        assertSame(handler, config.getToolExecutionErrorHandler());
    }

    @Test
    public void testToolArgumentsErrorHandler() {
        AgentConfiguration config = new AgentConfiguration();
        assertNull(config.getToolArgumentsErrorHandler());

        ToolArgumentsErrorHandler handler = (error, context) -> null;
        AgentConfiguration result = config.withToolArgumentsErrorHandler(handler);

        assertSame(config, result);
        assertSame(handler, config.getToolArgumentsErrorHandler());
    }

    @Test
    public void testCompensateOnToolErrors() {
        AgentConfiguration config = new AgentConfiguration();
        assertNull(config.getCompensateOnToolErrors());

        AgentConfiguration result = config.withCompensateOnToolErrors(true);

        assertSame(config, result);
        assertTrue(config.getCompensateOnToolErrors());
    }

    @Test
    public void testAiServicesCustomizer() {
        AgentConfiguration config = new AgentConfiguration();
        assertNull(config.getAiServicesCustomizer());

        AtomicBoolean invoked = new AtomicBoolean(false);
        AgentConfiguration result = config.withAiServicesCustomizer(builder -> invoked.set(true));

        assertSame(config, result);
        assertNotNull(config.getAiServicesCustomizer());
    }

    @Test
    public void testFluentChaining() {
        ToolExecutionErrorHandler execHandler = (error, context) -> null;
        ToolArgumentsErrorHandler argsHandler = (error, context) -> null;

        AgentConfiguration config = new AgentConfiguration()
                .withMaxToolCallingRoundTrips(5)
                .withToolExecutionErrorHandler(execHandler)
                .withToolArgumentsErrorHandler(argsHandler)
                .withCompensateOnToolErrors(true)
                .withAiServicesCustomizer(builder -> {
                });

        assertEquals(5, config.getMaxToolCallingRoundTrips());
        assertSame(execHandler, config.getToolExecutionErrorHandler());
        assertSame(argsHandler, config.getToolArgumentsErrorHandler());
        assertTrue(config.getCompensateOnToolErrors());
        assertNotNull(config.getAiServicesCustomizer());
    }
}
