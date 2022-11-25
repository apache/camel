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

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.StaticService;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.SingleInputTypedLanguageSupport;

@Language("jq")
public class JqLanguage extends SingleInputTypedLanguageSupport implements StaticService {

    @Override
    public void start() {
        // noop
    }

    @Override
    public void stop() {
        // noop
    }

    @Override
    public Predicate createPredicate(String expression) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression));
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression, properties));
    }

    @Override
    public Expression createExpression(String expression) {
        JqExpression answer = new JqExpression(expression);
        answer.setResultType(getResultType());
        answer.setHeaderName(getHeaderName());
        answer.setPropertyName(getPropertyName());
        answer.init(getCamelContext());
        return answer;
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        JqExpression answer = new JqExpression(expression);
        answer.setResultType(property(Class.class, properties, 0, getResultType()));
        answer.setHeaderName(property(String.class, properties, 1, getHeaderName()));
        answer.setPropertyName(property(String.class, properties, 2, getPropertyName()));
        answer.init(getCamelContext());
        return answer;
    }
}
