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
package org.apache.camel.language.xpath;

import java.lang.annotation.Annotation;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.support.language.DefaultAnnotationExpressionFactory;
import org.apache.camel.support.language.LanguageAnnotation;
import org.apache.camel.support.language.NamespacePrefix;
import org.apache.camel.util.ObjectHelper;

/**
 * Factory for the XPath expression annotations.
 */
public class XPathAnnotationExpressionFactory extends DefaultAnnotationExpressionFactory {

    @Override
    public Expression createExpression(CamelContext camelContext, Annotation annotation, LanguageAnnotation languageAnnotation, Class<?> expressionReturnType) {
        String xpath = getExpressionFromAnnotation(annotation);
        
        Class<?> resultType = getResultType(annotation);
        if (resultType.equals(Object.class)) {
            resultType = expressionReturnType;
        }
        
        XPathBuilder builder = XPathBuilder.xpath(xpath, resultType);        
        NamespacePrefix[] namespaces = getExpressionNameSpacePrefix(annotation);
        if (namespaces != null) {
            for (NamespacePrefix namespacePrefix : namespaces) {
                builder = builder.namespace(namespacePrefix.prefix(), namespacePrefix.uri());
            }
        }

        // Set the header name that we want the XPathBuilder to apply the XPath expression to
        String headerName = getHeaderName(annotation);
        if (ObjectHelper.isNotEmpty(headerName)) {
            builder.setHeaderName(headerName);
        }
        
        return builder;
    }

    protected Class<?> getResultType(Annotation annotation) {
        return (Class<?>) getAnnotationObjectValue(annotation, "resultType");
    }
    
    protected NamespacePrefix[] getExpressionNameSpacePrefix(Annotation annotation) {
        return (NamespacePrefix[]) getAnnotationObjectValue(annotation, "namespaces");
    }
    
    /**
     * Extracts the value of the header method in the Annotation. For backwards
     * compatibility this method will return null if the annotation's method is
     * not found.
     * 
     * @return If the annotation has the method 'header' then the name of the
     *         header we want to apply the XPath expression to. Otherwise null
     *         will be returned
     */
    protected String getHeaderName(Annotation annotation) {
        String headerValue = null;
        try {
            headerValue = (String)getAnnotationObjectValue(annotation, "headerName");
        } catch (Exception e) {
            // Do Nothing
        }
        return headerValue;
    }
}
