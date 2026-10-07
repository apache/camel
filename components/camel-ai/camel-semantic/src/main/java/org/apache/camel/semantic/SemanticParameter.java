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
package org.apache.camel.semantic;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A parameter supplied by the application, separate from expert deployment configuration. Map and list values can
 * contain nested values. Constraints requiring knowledge of their contents are validated by the expert.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface SemanticParameter {
    String name();

    String description();

    /** String, Boolean, Number, Map or List. Numeric wrapper classes are not portable across DSLs. */
    Class<?> type() default String.class;

    /** Require a mathematical integer for a Number parameter, regardless of its DSL representation. */
    boolean integer() default false;

    /** Type of map values or list elements, if constrained. */
    Class<?> itemType() default Object.class;

    boolean required() default false;

    /** Documented behaviour when omitted. Camel does not insert a default value. */
    String omission() default "";

    double minimum() default Double.NEGATIVE_INFINITY;

    double maximum() default Double.POSITIVE_INFINITY;

    int minSize() default 0;

    int maxSize() default Integer.MAX_VALUE;

    /** Allowed values for a String parameter; empty leaves the vocabulary unrestricted. */
    String[] values() default {};
}
