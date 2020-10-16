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

import java.lang.annotation.Annotation;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.support.language.DefaultAnnotationExpressionFactory;
import org.apache.camel.support.language.LanguageAnnotation;

public class JoorAnnotationExpressionFactory extends DefaultAnnotationExpressionFactory {

    @Override
    public Expression createExpression(
            CamelContext camelContext, Annotation annotation,
            LanguageAnnotation languageAnnotation, Class<?> expressionReturnType) {

        String expression = getExpressionFromAnnotation(annotation);
        JoorExpression answer = new JoorExpression(expression);

        if (expressionReturnType != null) {
            answer.setResultType(expressionReturnType);
        }

        if (annotation instanceof Joor) {
            Joor joorAnnotation = (Joor) annotation;
            answer.setPreCompile(joorAnnotation.preCompile());
            answer.setSingleQuotes(joorAnnotation.singleQuotes());
        }

        answer.init(camelContext);
        return answer;
    }

}
