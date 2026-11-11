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
package org.apache.camel.sanitizer.junit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.camel.sanitizer.Violation;

/**
 * Says that a test breaks a rule of the exchange sanitizer on purpose, such as a test of a fix whose processor throws
 * from {@code process}. The extension then checks that the violation was reported during the test, instead of failing
 * the test with it:
 *
 * <pre>
 * &#64;Test
 * &#64;ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "process1")
 * void aProcessorThatThrowsFailsTheExchange() { ... }
 * </pre>
 *
 * The test fails if no violation of an expected kind (and step, if given) was reported, or if a violation was reported
 * that no expectation of the test matches. Repeat the annotation when a test breaks more than one rule. It has an
 * effect only when the extension runs ({@link CamelSanitizer}, or the CI switch); an expected violation is not required
 * when the contexts of the test class stayed off on purpose (profile {@code prod},
 * {@code camel.sanitizer.enabled=false}, pooled exchanges) and none of them installed the sanitizer.
 *
 * @since 4.24
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD })
@Repeatable(ExpectSanitizerViolations.class)
public @interface ExpectSanitizerViolation {

    /**
     * The kind of violation the test breaks on purpose. When more than one is given, any of them meets the expectation,
     * for a test whose processor both throws and calls its callback, on two threads, where the order the sanitizer sees
     * decides the kind: {@code THREW_BEFORE_CALLBACK} then {@code CALLBACK_AFTER_THROW}, or
     * {@code THREW_AFTER_CALLBACK}.
     */
    Violation[] value();

    /**
     * The id of the step where it is reported, such as {@code process1}; any step when empty.
     */
    String step() default "";
}
