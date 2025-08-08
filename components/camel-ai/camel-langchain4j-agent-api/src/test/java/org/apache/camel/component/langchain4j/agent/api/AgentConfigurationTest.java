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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        assertTrue(result.contains(java.io.Serializable.class));
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
        List<Class<?>> result = AgentConfiguration.parseGuardrailClasses(null);

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
        assertTrue(result.contains(java.io.Serializable.class));
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
        assertTrue(result.contains(java.io.Serializable.class));
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
}
