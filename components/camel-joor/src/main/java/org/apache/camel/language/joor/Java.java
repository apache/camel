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
package org.apache.camel.language.joor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.camel.support.language.LanguageAnnotation;

/**
 * Used to inject a Java (joor) expression into a field, property, method or parameter when using Bean Integration.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
@LanguageAnnotation(language = "java", factory = JavaAnnotationExpressionFactory.class)
public @interface Java {

    String value();

    /**
     * Whether the expression should be pre compiled once during initialization phase. If this is turned off, then the
     * expression is reloaded and compiled on each evaluation.
     */
    boolean preCompile() default true;

    /**
     * Whether single quotes can be used as replacement for double quotes. This is convenient when you need to work with
     * strings inside strings.
     */
    boolean singleQuotes() default true;

    /**
     * The desired return type.
     */
    Class<?> resultType() default Object.class;
}
