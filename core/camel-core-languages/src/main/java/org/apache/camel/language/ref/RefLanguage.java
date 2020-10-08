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
package org.apache.camel.language.ref;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.PredicateToExpressionAdapter;
import org.apache.camel.support.builder.ExpressionBuilder;

/**
 * A language for referred expressions or predicates.
 */
@org.apache.camel.spi.annotations.Language("ref")
public class RefLanguage extends LanguageSupport {

    @Override
    public Predicate createPredicate(String expression) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression));
    }

    @Override
    public Expression createExpression(final String expression) {
        Expression answer = new ExpressionAdapter() {

            private Expression exp;

            @Override
            public void init(CamelContext context) {
                exp = ExpressionBuilder.refExpression(expression);
                exp.init(context);
            }

            public Object evaluate(Exchange exchange) {
                Expression target = null;

                Object lookup = exp.evaluate(exchange, Object.class);

                // must favor expression over predicate
                if (lookup instanceof Expression) {
                    target = (Expression) lookup;
                } else if (lookup instanceof Predicate) {
                    target = PredicateToExpressionAdapter.toExpression((Predicate) lookup);
                }

                if (target != null) {
                    return target.evaluate(exchange, Object.class);
                } else {
                    throw new IllegalArgumentException(
                            "Cannot find expression or predicate in registry with ref: " + expression);
                }
            }

            public String toString() {
                return exp.toString();
            }
        };

        answer.init(getCamelContext());
        return answer;
    }

}
