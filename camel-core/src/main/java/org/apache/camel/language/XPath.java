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
package org.apache.camel.language;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.camel.component.bean.XPathAnnotationExpressionFactory;

/**
 * Used to inject an XPath expression into a field, property, method or parameter when using
 * <a href="http://camel.apache.org/bean-integration.html">Bean Integration</a>.
 *
 * @version 
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@LanguageAnnotation(language = "xpath", factory = XPathAnnotationExpressionFactory.class)
public @interface XPath {
    /**
     * @return The XPath which will be applied
     */
    String value();

    NamespacePrefix[] namespaces() default {
    @NamespacePrefix(prefix = "soap", uri = "http://www.w3.org/2003/05/soap-envelope"),
    @NamespacePrefix(prefix = "xsd", uri = "http://www.w3.org/2001/XMLSchema")};
    
    Class<?> resultType() default Object.class;
    
    /**
     * @return The name of the header we want to apply the XPath expression to.
     *  If this is empty then the XPath expression will be applied to the body instead.
     */
    String headerName() default "";
}