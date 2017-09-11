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
package org.apache.camel.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta data for EIPs, components, data formats and other Camel concepts
 * <p/>
 * For example to associate labels to Camel components
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface Metadata {

    /**
     * A human display name of the parameter.
     * <p/>
     * This is used for documentation and tooling only.
     */
    String displayName() default "";

    /**
     * To define one or more labels.
     * <p/>
     * Multiple labels can be defined as a comma separated value.
     */
    String label() default "";

    /**
     * To define a default value.
     */
    String defaultValue() default "";

    /**
     * To define that this entity is required.
     */
    String required() default "";

    /**
     * An optional human readable title of this entity, to be used instead of a computed title.
     */
    String title() default "";

    /**
     * Returns a description of this entity.
     * <p/>
     * This is used for documentation and tooling only.
     */
    String description() default "";

    /**
     * Allows to define enums this options accepts.
     * <p/>
     * If the type is already an enum, then this option should not be used; instead you can use
     * this option when the type is a String that only accept certain values.
     * <p/>
     * Multiple values is separated by comma.
     */
    String enums() default "";

    /**
     * Whether the option is secret/sensitive information such as a password.
     */
    boolean secret() default false;

    /**
     * To re-associate the preferred Java type of this parameter.
     * <p/>
     * This is used for parameters which are of a specialized type but can be configured by another Java type, such as from a String.
     */
    String javaType() default "";

    /**
     * The first version this functionality was added to Apache Camel.
     */
    String firstVersion() default "";

    /**
     * Additional description that can explain the user about the deprecation and give reference to what to use instead.
     */
    String deprecationNode() default "";

}
