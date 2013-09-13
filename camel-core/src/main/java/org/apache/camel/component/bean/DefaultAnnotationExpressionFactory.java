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
import org.apache.camel.Predicate;
import org.apache.camel.language.LanguageAnnotation;
import org.apache.camel.spi.Language;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PredicateToExpressionAdapter;

/**
 * Default implementation of the {@link AnnotationExpressionFactory}.
 *
 * @version 
 */
public class DefaultAnnotationExpressionFactory implements AnnotationExpressionFactory {

    public Expression createExpression(CamelContext camelContext, Annotation annotation, LanguageAnnotation languageAnnotation, Class<?> expressionReturnType) {
        String languageName = languageAnnotation.language();
        if (languageName == null) {
            throw new IllegalArgumentException("Cannot determine the language from the annotation: " + annotation);
        }
        Language language = camelContext.resolveLanguage(languageName);
        if (language == null) {
            throw new IllegalArgumentException("Cannot find the language: " + languageName + " on the classpath");
        }
        String expression = getExpressionFromAnnotation(annotation);

        if (expressionReturnType == Boolean.class || expressionReturnType == boolean.class) {
            Predicate predicate = language.createPredicate(expression);
            return PredicateToExpressionAdapter.toExpression(predicate);
        } else {
            return language.createExpression(expression);
        }
    }

    protected String getExpressionFromAnnotation(Annotation annotation) {
        Object value = getAnnotationObjectValue(annotation, "value");
        if (value == null) {
            throw new IllegalArgumentException("Cannot determine the expression from the annotation: " + annotation);
        }
        return value.toString();
    }
    
    /**
     * @param annotation The annotation to get the value of 
     * @param methodName The annotation name 
     * @return The value of the annotation
     */
    protected Object getAnnotationObjectValue(Annotation annotation, String methodName) {        
        try {
            Method method = annotation.getClass().getMethod(methodName);
            Object value = ObjectHelper.invokeMethod(method, annotation);
            return value;
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot determine the Object value of the annotation: " + annotation
                + " as it does not have the method: " + methodName + "() method", e);
        }
    }
}
