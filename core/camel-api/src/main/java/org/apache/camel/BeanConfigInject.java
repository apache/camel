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
 * Marks a field, method, constructor, or parameter as an injection point for a configuration bean.
 * <p/>
 * {@link #value()} specifies the root property prefix (e.g. {@code camel.component.mycomp}). During bean
 * post-processing Camel first looks for an existing instance in the {@link org.apache.camel.spi.Registry}; if none is
 * found it creates a new instance of the declared type and binds all matching properties from that prefix using Camel's
 * property-binding mechanism. This is the standard way to inject typed configuration objects into components and custom
 * services.
 *
 * @see   BeanInject
 * @see   PropertyInject
 * @since 3.2
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PARAMETER })
public @interface BeanConfigInject {

    /**
     * Name of the root property (prefix)
     */
    String value();

}
