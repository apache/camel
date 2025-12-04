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

package org.apache.camel.model.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class RestDefinitionTest {

    @Test
    void typeClassTest() {
        RestDefinition rest = new RestDefinition().get();
        VerbDefinition verb = rest.getVerbs().get(0);

        // Plain classes
        rest.outType(String.class);
        assertEquals(String.class, verb.getOutTypeClass());
        assertEquals("java.lang.String", verb.getOutType());

        rest.outType(RestDefinitionTest.class);
        assertEquals(RestDefinitionTest.class, verb.getOutTypeClass());
        assertEquals(
                "org.apache.camel.model.rest.RestDefinitionTest",
                rest.getVerbs().get(0).getOutType());

        // Nested classes (CAMEL-15199)
        rest.outType(TestType.class);
        assertEquals(TestType.class, verb.getOutTypeClass());
        assertEquals(
                "org.apache.camel.model.rest.RestDefinitionTest$TestType",
                rest.getVerbs().get(0).getOutType());

        // Primitives
        rest.outType(int.class);
        assertEquals(int.class, verb.getOutTypeClass());
        assertEquals("int", verb.getOutType());

        // Object array
        rest.outType(String[].class);
        assertEquals(String[].class, verb.getOutTypeClass());
        assertEquals("java.lang.String[]", verb.getOutType());

        // Nested object array
        rest.outType(TestType[].class);
        assertEquals(TestType[].class, verb.getOutTypeClass());
        assertEquals(
                "org.apache.camel.model.rest.RestDefinitionTest$TestType[]",
                rest.getVerbs().get(0).getOutType());

        // Primitive array (CAMEL-20732)
        rest.outType(byte[].class);
        assertEquals(byte[].class, verb.getOutTypeClass());
        assertEquals("byte[]", verb.getOutType());
    }

    private static class TestType {
        // empty class for testing nested types
    }
}
