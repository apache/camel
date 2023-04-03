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
package org.apache.camel.dsl.java.joor;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The unit test for {@link CompilationUnit}.
 */
class CompilationUnitTest {

    private void compile(String content, String innerClassName) {
        CompilationUnit unit = CompilationUnit.input();
        String outerClassName = "com.foo.OuterClass";
        unit.addClass(outerClassName, content);
        CompilationUnit.Result result = MultiCompile.compileUnit(unit);
        assertEquals(Set.of(outerClassName), result.getClassNames());
        assertEquals(Set.of(outerClassName, outerClassName + "$" + innerClassName), result.getCompiledClassNames());
    }

    @Test
    void shouldSupportNestedInnerClass() {
        compile(
                """
                        package com.foo;
                        class OuterClass {
                           public class InnerClass {
                           }
                         }
                        """,
                "InnerClass");
    }

    @Test
    void shouldSupportStaticNestedClass() {
        compile(
                """
                        package com.foo;
                        class OuterClass {
                           public static class InnerClass {
                           }
                         }
                        """,
                "InnerClass");
    }

    @Test
    void shouldSupportMethodLocalInnerClass() {
        compile(
                """
                        package com.foo;
                        class OuterClass {
                            void outerClassMethod() {
                                class InnerClass {
                                    void innerClassMethod() {

                                    }
                                }
                            }
                        }
                        """,
                "1InnerClass");
    }

    @Test
    void shouldSupportAnonymousInnerClass() {
        compile(
                """
                        package com.foo;
                        class OuterClass {
                            void outerClassMethod() {
                                Object o = new Object(){};
                            }
                        }
                        """,
                "1");
    }
}
