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
package org.apache.camel.test.main.junit5;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @ReplaceInRegistry} is an annotation used to mark all the methods and fields whose return value or value
 * should replace an existing bean in the registry. It is meant to be used to replace a real implementation of a service
 * with a mock or a test implementation.
 * <p/>
 * If a field is marked with the annotation {@code @ReplaceInRegistry}, the name and the type of the field are used to
 * identify the bean to replace, and the value of the field is the new value of the bean. The field can be in the test
 * class or in a parent class.
 * <p/>
 * In the next example, the annotation {@code ReplaceInRegistry} on the field {@code myGreetings} of type
 * {@code Greetings} indicates that the bean with the same name and type should be replaced by an instance of
 * {@code CustomGreetings}.
 *
 * <pre>
 * <code>
 *
 * &#64;CamelMainTest
 * class SomeTest {
 *
 *     &#64;ReplaceInRegistry
 *     Greetings myGreetings = new CustomGreetings("Willy");
 *
 *     // The rest of the test class
 * }
 * </code>
 * </pre>
 * <p/>
 * If a method is marked with the annotation {@code @ReplaceInRegistry}, the name and the return type of the method are
 * used to identify the bean to replace, and the return value of the method is the new value of the bean. The method can
 * be in the test class or in a parent class.
 * <p/>
 * In the next example, the annotation {@code ReplaceInRegistry} on the method {@code myGreetings} whose return type is
 * {@code Greetings} indicates that the bean with the same name and type should be replaced by an instance of
 * {@code CustomGreetings}.
 *
 * <pre>
 * <code>
 *
 * &#64;CamelMainTest
 * class SomeTest {
 *
 *     &#64;PropertyInject("name")
 *     String name;
 *
 *     &#64;ReplaceInRegistry
 *     Greetings myGreetings() {
 *         return new CustomGreetings(name);
 *     }
 *
 *     // The rest of the test class
 * }
 * </code>
 * </pre>
 * <p/>
 * This annotation can be used in {@code @Nested} test classes. The {@code @ReplaceInRegistry} annotations of outer
 * classes are processed before the {@code @ReplaceInRegistry} annotations of inner classes.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.FIELD })
public @interface ReplaceInRegistry {
}
