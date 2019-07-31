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
package org.apache.camel.test.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Indicates to set the shutdown timeout of all {@code CamelContext}s instantiated through the 
 * use of Spring Test loaded application contexts.  If no annotation is used, the timeout is
 * automatically reduced to 10 seconds by the test framework.  If the annotation is present the
 * shutdown timeout is set based on the value of {@link #value()}. 
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ShutdownTimeout {

    /**
     * The shutdown timeout to set on the {@code CamelContext}(s).
     * Defaults to {@code 10} seconds.
     */
    int value() default 10;
    
    /**
     * The time unit that {@link #value()} is in.
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
