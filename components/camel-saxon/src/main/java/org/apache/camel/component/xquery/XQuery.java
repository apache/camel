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
package org.apache.camel.component.xquery;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.camel.support.language.LanguageAnnotation;
import org.apache.camel.support.language.NamespacePrefix;

/**
 * An annotation for injection of an XQuery expressions into a field, property, method or parameter when using
 * <a href="http://camel.apache.org/bean-integration.html">Bean Integration</a>.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@LanguageAnnotation(language = "xquery", factory = XQueryAnnotationExpressionFactory.class)
public @interface XQuery {
    String value();
    boolean stripsAllWhiteSpace() default true;

    NamespacePrefix[] namespaces() default {
        @NamespacePrefix(prefix = "soap", uri = "http://www.w3.org/2003/05/soap-envelope"),
        @NamespacePrefix(prefix = "xsd", uri = "http://www.w3.org/2001/XMLSchema")
    };
    
    /**
     * @return The name of the header we want to apply the Xquery expression to.
     *  If this is empty then the Xquery expression will be applied to the body instead.
     */
    String headerName() default "";
}
