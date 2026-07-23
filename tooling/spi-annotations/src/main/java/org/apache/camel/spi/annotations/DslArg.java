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
package org.apache.camel.spi.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field in a Camel model Definition class as a primary argument for the Java DSL method call.
 *
 * Primary arguments appear inside the method parentheses (e.g., {@code filter(simple("..."))} rather than as chained
 * attribute calls (e.g., {@code .timeout("5000")}).
 *
 * The Java DSL model writer code generator reads this annotation at build time to determine which fields to render as
 * method arguments and how to render them.
 *
 * @since 4.21
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ ElementType.FIELD, ElementType.TYPE })
public @interface DslArg {

    /**
     * Ordering position when multiple primary args exist in the same method call. Lower values come first. Default is
     * 0.
     */
    int position() default 0;

    /**
     * How to render this field in generated Java DSL source code.
     * <ul>
     * <li>{@code "string"} — quoted string: {@code quote(value)}</li>
     * <li>{@code "class"} — class literal: {@code MyClass.class}</li>
     * <li>{@code "expression"} — expression DSL: {@code simple("...")}</li>
     * <li>{@code "expressionSub"} — expression sub-element: unwrap ExpressionSubElementDefinition</li>
     * <li>{@code "enumString"} — enum constant from string: {@code LoggingLevel.INFO}</li>
     * <li>{@code "long"} — numeric literal (no quotes)</li>
     * <li>{@code "classList"} — list of class literals: {@code Exception1.class, Exception2.class}</li>
     * </ul>
     * Empty string means auto-detect from the field's Java type.
     */
    String renderType() default "";

    /**
     * For {@code enumString} render type: the simple name of the enum class (e.g., "ExchangePattern", "LoggingLevel").
     * This is used to generate the qualified enum constant reference in the Java DSL output.
     */
    String typeName() default "";

    /**
     * Whether this is a primary arg (inside method parentheses). Default is true.
     */
    boolean primary() default true;

    /**
     * When placed on a type (class), lists field names from superclasses whose {@code @DslArg} annotations should be
     * ignored for this class. This allows subclasses to suppress inherited primary arguments that don't apply to their
     * DSL method signature.
     */
    String[] exclude() default {};
}
