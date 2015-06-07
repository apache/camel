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
package org.apache.camel.test.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.camel.CamelContext;

/**
 * Indicates the use of {@code adviceWith()} within the test class.  If a class is annotated with
 * this annotation and {@link UseAdviceWith#value()} returns true, any 
 * {@code CamelContext}s bootstrapped during the test through the use of Spring Test loaded 
 * application contexts will not be started automatically.  The test author is responsible for 
 * injecting the Camel contexts into the test and executing {@link CamelContext#start()} on them 
 * at the appropriate time after any advice has been applied to the routes in the Camel context(s). 
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface UseAdviceWith {
    
    /**
     * Whether the test annotated with this annotation should be treated as if 
     * {@code adviceWith()} is in use in the test and the Camel contexts should not be started
     * automatically.
     * Defaults to {@code true}.
     */
    boolean value() default true;
}
