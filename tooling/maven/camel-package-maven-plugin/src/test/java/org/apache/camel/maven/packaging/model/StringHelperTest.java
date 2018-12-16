/**
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
package org.apache.camel.maven.packaging.model;

import java.util.stream.Stream;

import org.apache.camel.maven.packaging.StringHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.params.provider.Arguments.arguments;

public class StringHelperTest {

    static Stream<Arguments> getClassShortNameTypeVarations() {
        return Stream.of(
                         arguments("String", "String"),
                         arguments("String", "java.lang.String"),
                         arguments("List", "List<String>"),
                         arguments("List", "java.util.List<String>"),
                         arguments("List", "List<java.lang.String>"),
                         arguments("List", "java.util.List.List<org.apache.camel.Exchange>"),
                         arguments("List", "java.util.List<Map<String,Integer>>"),
                         arguments("List", "java.util.List<Map<java.lang.String,Integer>>"),
                         arguments("List", "java.util.List<Map<String,java.lang.Integer>>"),
                         arguments("List", "java.util.List<Map<java.lang.String,java.lang.Integer>>"),
                         arguments("List", "java.util.List<java.util.Map<java.lang.String,java.lang.Integer>>"));
    };

    @ParameterizedTest
    @MethodSource("getClassShortNameTypeVarations")
    public void getClassShortName(String expectedBaseClassName, String className) {
        Assertions.assertEquals(expectedBaseClassName, StringHelper.getClassShortName(className));
    }
}
