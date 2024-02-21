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
package org.apache.camel.jsonpath;

import java.lang.annotation.Annotation;

import com.jayway.jsonpath.Option;
import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.support.language.DefaultAnnotationExpressionFactory;
import org.apache.camel.support.language.LanguageAnnotation;

public class JsonPathAnnotationExpressionFactory extends DefaultAnnotationExpressionFactory {

    @Override
    public Expression createExpression(
            CamelContext camelContext, Annotation annotation,
            LanguageAnnotation languageAnnotation, Class<?> expressionReturnType) {

        String expression = getExpressionFromAnnotation(annotation);
        JsonPathExpression answer = new JsonPathExpression(expression);

        Class<?> resultType = getResultType(annotation);
        if (resultType.equals(Object.class)) {
            resultType = expressionReturnType;
        }
        if (resultType != null) {
            answer.setResultType(resultType);
        }

        if (annotation instanceof JsonPath) {
            JsonPath jsonPathAnnotation = (JsonPath) annotation;

            answer.setSuppressExceptions(jsonPathAnnotation.suppressExceptions());
            answer.setAllowSimple(jsonPathAnnotation.allowSimple());
            Option[] options = jsonPathAnnotation.options();
            answer.setOptions(options);

            String source = getSource(annotation);
            if (source != null) {
                answer.setSource(ExpressionBuilder.singleInputExpression(source));
            }
        }

        answer.init(camelContext);
        return answer;
    }

    private Class<?> getResultType(Annotation annotation) {
        return (Class<?>) getAnnotationObjectValue(annotation, "resultType");
    }

    protected String getSource(Annotation annotation) {
        String answer = null;
        try {
            answer = (String) getAnnotationObjectValue(annotation, "source");
        } catch (Exception e) {
            // Do Nothing
        }
        if (answer != null && answer.isBlank()) {
            return null;
        }
        return answer;
    }

}
