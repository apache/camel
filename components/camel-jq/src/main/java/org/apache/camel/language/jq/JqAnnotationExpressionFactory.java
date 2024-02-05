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
package org.apache.camel.language.jq;

import java.lang.annotation.Annotation;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.support.language.DefaultAnnotationExpressionFactory;
import org.apache.camel.support.language.LanguageAnnotation;
import org.apache.camel.util.ObjectHelper;

public class JqAnnotationExpressionFactory extends DefaultAnnotationExpressionFactory {

    @Override
    public Expression createExpression(
            CamelContext camelContext, Annotation annotation,
            LanguageAnnotation languageAnnotation, Class<?> expressionReturnType) {

        String expression = getExpressionFromAnnotation(annotation);
        JqExpression answer = new JqExpression(expression);

        Class<?> resultType = getResultType(annotation);
        if (resultType.equals(Object.class)) {
            resultType = expressionReturnType;
        }
        if (resultType != null) {
            answer.setResultType(resultType);
        }

        if (annotation instanceof Jq) {
            Jq jqAnnotation = (Jq) annotation;

            String variableName = null;
            String headerName = null;
            String propertyName = null;
            if (ObjectHelper.isNotEmpty(jqAnnotation.variableName())) {
                variableName = jqAnnotation.variableName();
            }
            if (ObjectHelper.isNotEmpty(jqAnnotation.headerName())) {
                headerName = jqAnnotation.headerName();
            }
            if (ObjectHelper.isNotEmpty(jqAnnotation.propertyName())) {
                propertyName = jqAnnotation.propertyName();
            }
            if (variableName != null || headerName != null || propertyName != null) {
                answer.setSource(ExpressionBuilder.singleInputExpression(variableName, headerName, propertyName));
            }
        }

        answer.init(camelContext);
        return answer;
    }

    private Class<?> getResultType(Annotation annotation) {
        return (Class<?>) getAnnotationObjectValue(annotation, "resultType");
    }
}
