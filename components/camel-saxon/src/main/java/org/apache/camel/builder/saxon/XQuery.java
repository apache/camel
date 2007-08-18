/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder.saxon;

import org.apache.camel.language.LanguageAnnotation;
import org.apache.camel.language.NamespacePrefix;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation for injection of XQuery expressions
 * into method parameters, fields or properties
 *
 * @version $Revision: 1.1 $
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@LanguageAnnotation(language = "xquery", factory = XQueryAnnotationExpressionFactory.class)
public @interface XQuery {
    public String value();

    public NamespacePrefix[] namespaces() default {
    @NamespacePrefix(prefix = "soap", uri = "http://www.w3.org/2003/05/soap-envelope"),
    @NamespacePrefix(prefix = "xsd", uri = "http://www.w3.org/2001/XMLSchema")};
}
