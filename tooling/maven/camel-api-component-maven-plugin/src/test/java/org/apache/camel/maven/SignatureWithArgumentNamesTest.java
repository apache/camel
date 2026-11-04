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
package org.apache.camel.maven;

import java.util.List;
import java.util.Map;

import org.apache.camel.support.component.ApiMethodArg;
import org.junit.jupiter.api.Test;

import static org.apache.camel.maven.AbstractApiMethodGeneratorMojo.getSignatureWithArgumentNames;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the argument names in the signatures written to the catalog are the names after substitution.
 */
public class SignatureWithArgumentNamesTest {

    @Test
    public void testSubstitutedName() {
        assertEquals("Modify modify(String userId, String id, ModifyMessageRequest modifyMessageRequest)",
                getSignatureWithArgumentNames("Modify modify(String userId, String id, ModifyMessageRequest content)",
                        List.of(arg("userId", String.class), arg("id", String.class),
                                arg("modifyMessageRequest", Object.class))));
    }

    @Test
    public void testGenericsWithComma() {
        assertEquals("Map<String, Object> put(java.util.Map<String, java.util.List<Object>> values, int count)",
                getSignatureWithArgumentNames(
                        "Map<String, Object> put(java.util.Map<String, java.util.List<Object>> content, int size)",
                        List.of(arg("values", Map.class), arg("count", int.class))));
    }

    @Test
    public void testExtraOptionsAfterSignatureArguments() {
        // an extra option included for the method is added after the arguments of the signature
        assertEquals("Get get(String fileId)",
                getSignatureWithArgumentNames("Get get(String fileId)",
                        List.of(arg("fileId", String.class), arg("extraOption", String.class))));
    }

    @Test
    public void testUnchanged() {
        assertEquals("String sayHi()", getSignatureWithArgumentNames("String sayHi()", List.of()));
        assertEquals("String[] greetTimes(String name, int times)",
                getSignatureWithArgumentNames("String[] greetTimes(String name, int times)",
                        List.of(arg("name", String.class), arg("times", int.class))));
        // does not match the arguments so keep the signature as-is
        assertEquals("String greetUs(String name1, String name2)",
                getSignatureWithArgumentNames("String greetUs(String name1, String name2)",
                        List.of(arg("firstName", String.class))));
    }

    private static ApiMethodArg arg(String name, Class<?> type) {
        return new ApiMethodArg(name, type, null, type.getName(), null);
    }
}
