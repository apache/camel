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
package org.apache.camel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used for binding a bean to the registry.
 *
 * This annotation is not supported with camel-spring or camel-spring-boot as they have
 * their own set of annotations for registering beans in spring bean registry.
 * Instead this annotation is intended for Camel standalone such as camel-main or camel-quarkus
 * or similar runtimes.
 *
 * If no name is specified then the bean will have its name auto computed based on the
 * class name, field name, or method name where the annotation is configured.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface BindToRegistry {

    /**
     * The name of the bean
     */
    String value() default "";

    /**
     * Whether to perform bean post processing (dependency injection) on the bean
     */
    boolean beanPostProcess() default false;
}
