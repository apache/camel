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

import org.apache.camel.main.MainConfigurationProperties;

/**
 * {@code @Configure} is an annotation allowing to mark a method with an arbitrary name that is meant to be called to
 * configure the Camel context to use for the test. Several methods in the test class and/or its parent classes can be
 * annotated.
 * <p/>
 * Be aware that only a specific signature is supported, indeed any annotated methods must have only one parameter of
 * type {@link MainConfigurationProperties}.
 * <p/>
 * In the next example, the annotation {@code Configure} on the method {@code configureRoutesBuilder} indicates the test
 * framework to call it while initializing the Camel context used for the test.
 *
 * <pre>
 * <code>
 *
 * &#64;CamelMainTest
 * class SomeTest {
 *
 *     &#64;Configure
 *     void configureRoutesBuilders(MainConfigurationProperties configuration) {
 *         configuration.addRoutesBuilder(new SomeRouteBuilder());
 *     }
 *
 *     // The rest of the test class
 * }
 * </code>
 * </pre>
 * <p/>
 * This annotation can be used in {@code @Nested} test classes. The configure methods of outer classes are executed
 * before the configure methods of inner classes.
 *
 * @see MainConfigurationProperties
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Configure {
}
