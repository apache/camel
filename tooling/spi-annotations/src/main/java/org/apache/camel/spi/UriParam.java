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
 * Represents an injection point of a Camel Uri parameter value on an Endpoint or Consumer, usually configured via a URI style query parameter in a URI
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.FIELD })
public @interface UriParam {

    /**
     * Returns the name of the parameter.
     * <p/>
     * If this is not specified then the name of the field or property which has this annotation is used.
     */
    String name() default "";

    /**
     * A human display name of the parameter.
     * <p/>
     * This is used for documentation and tooling only.
     */
    String displayName() default "";

    /**
     * The default value of the parameter.
     * <p/>
     * Note that this attribute is only for documentation purpose. The default value in use at runtime is the value the Java field was assigned.
     */
    String defaultValue() default "";

    /**
     * A special note about the default value.
     * <p/>
     * This can be used to document special cases about the default value.
     */
    String defaultValueNote() default "";

    /**
     * Returns a description of this parameter.
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
     * To associate this parameter with label(s).
     * <p/>
     * Multiple labels can be defined as a comma separated value.
     * <p/>
     * The labels is intended for grouping the parameters, such as <tt>consumer</tt>, <tt>producer</tt>, <tt>common</tt>, <tt>security</tt>, etc.
     */
    String label() default "";

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
     * If the parameter can be configured multiple times, such as configuring options to a <tt>Map</tt> type.
     */
    boolean multiValue() default false;

    /**
     * If the parameter must be configured with a prefix.
     * <p/>
     * For example to configure scheduler options, the parameters is prefixed with <tt>scheduler.foo=bar</tt>
     */
    String prefix() default "";

    /**
     * If the parameter can be configured with an optional prefix.
     * <p/>
     * For example to configure consumer options, the parameters can be prefixed with <tt>consumer.</tt>, eg <tt>consumer.delay=5000</tt>
     */
    String optionalPrefix() default "";

}