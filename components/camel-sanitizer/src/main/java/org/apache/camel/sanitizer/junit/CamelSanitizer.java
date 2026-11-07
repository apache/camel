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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Turns the exchange sanitizer on for the CamelContexts a test class builds, and fails a test with the first violation
 * and the reports of the others (up to ten in all). It is the only change a test needs, besides the
 * {@code camel-sanitizer} test dependency:
 *
 * <pre>
 * &#64;CamelSanitizer
 * class PriceRouteTest extends CamelTestSupport {
 *     ...
 * }
 * </pre>
 *
 * A test that breaks a rule on purpose says so with {@link ExpectSanitizerViolation}. See
 * {@link CamelSanitizerExtension} for how a violation is charged to a test.
 *
 * @since 4.24
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
@ExtendWith(CamelSanitizerExtension.class)
public @interface CamelSanitizer {
}
