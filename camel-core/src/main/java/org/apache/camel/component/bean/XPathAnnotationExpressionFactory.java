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
package org.apache.camel.component.bean;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.builder.xml.XPathBuilder;
import org.apache.camel.language.LanguageAnnotation;
import org.apache.camel.language.NamespacePrefix;
import org.apache.camel.util.ObjectHelper;

/**
 * Factory for the XPath expression annotations.
 *
 * @version 
 */
public class XPathAnnotationExpressionFactory extends DefaultAnnotationExpressionFactory {

    @Override
    public Expression createExpression(CamelContext camelContext, Annotation annotation, LanguageAnnotation languageAnnotation, Class<?> expressionReturnType) {
        String xpath = getExpressionFromAnnotation(annotation);
        XPathBuilder builder = XPathBuilder.xpath(xpath, getResultType(annotation));
        NamespacePrefix[] namespaces = getExpressionNameSpacePrefix(annotation);
        if (namespaces != null) {
            for (NamespacePrefix namespacePrefix : namespaces) {
                builder = builder.namespace(namespacePrefix.prefix(), namespacePrefix.uri());
            }

        }
        return builder;
    }

    protected Class<?> getResultType(Annotation annotation) {
        try {
            Method method = annotation.getClass().getMethod("resultType");
            Object value = ObjectHelper.invokeMethod(method, annotation);
            return (Class<?>) value;
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot determine the annotation: " + annotation + " as it does not have an resultType() method", e);
        }
    }

    protected NamespacePrefix[] getExpressionNameSpacePrefix(Annotation annotation) {
        try {
            Method method = annotation.getClass().getMethod("namespaces");
            Object value = ObjectHelper.invokeMethod(method, annotation);
            return (NamespacePrefix[]) value;
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot determine the annotation: " + annotation + " as it does not have an namespaces() method", e);
        }
    }
}
