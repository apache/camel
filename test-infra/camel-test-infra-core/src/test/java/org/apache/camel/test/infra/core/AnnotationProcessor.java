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

package org.apache.camel.test.infra.core;

import java.lang.annotation.Annotation;

import org.apache.camel.CamelContext;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * A pluggable annotation processor
 */
public interface AnnotationProcessor {

    /**
     * Evaluates the given annotation in a test context to determine if it provides a CamelContext
     *
     * @param  extensionContext JUnit's extension context
     * @param  annotationClass  the annotation class that may indicate that a method within the test class provides a
     *                          custom CamelContext
     * @param  target           the target type of the CamelContext (usually CamelContext.class)
     * @return                  the provided CamelContext from the test class or null if none exists
     * @param  <T>              the type of the CamelContexts
     */
    <T> T setupContextProvider(
            ExtensionContext extensionContext, Class<? extends Annotation> annotationClass, Class<T> target);

    /**
     * Evaluates the methods in the test instance, invoking them if the given annotation is present. This can be used to
     * invoke methods annotated with the fixture annotations (i.e., RouteFixture, etc).
     *
     * @param extensionContext JUnit's extension context
     * @param annotationClass  the annotation class that may indicate that a method should be invoked
     * @param instance         the test instance
     * @param context          the CamelContext that will be passed to th
     */
    void evalMethod(
            ExtensionContext extensionContext, Class<? extends Annotation> annotationClass, Object instance,
            CamelContext context);

    /**
     * Evaluates the fields in the test instance, handling them in accordance with the given annotation (i.e., binding
     * to the registry, adding endpoints, etc)
     *
     * @param extensionContext JUnit's extension context
     * @param annotationClass  the annotation class that may indicate that a field needs to be evaluated
     * @param instance         the test instance
     * @param context
     */
    void evalField(
            ExtensionContext extensionContext, Class<? extends Annotation> annotationClass, Object instance,
            CamelContext context);
}
