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
import org.apache.camel.spi.Registry;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.PredicateToExpressionAdapter;
import org.apache.camel.support.TypedLanguageSupport;
import org.apache.camel.support.builder.ExpressionBuilder;

/**
 * A language for referred expressions or predicates.
 */
@org.apache.camel.spi.annotations.Language("ref")
public class RefLanguage extends TypedLanguageSupport {

    @Override
    public Predicate createPredicate(String expression) {
        if (hasSimpleFunction(expression)) {
            return createDynamic(expression);
        } else {
            return createStaticPredicate(expression);
        }
    }

    @Override
    public Expression createExpression(String expression) {
        if (hasSimpleFunction(expression)) {
            return createDynamic(expression);
        } else {
            return createStaticExpression(expression);
        }
    }

    protected Expression createStaticExpression(String expression) {
        Expression answer;

        Object exp = getCamelContext().getRegistry().lookupByName(expression);
        if (exp instanceof Expression) {
            answer = (Expression) exp;
        } else if (exp instanceof Predicate) {
            answer = PredicateToExpressionAdapter.toExpression((Predicate) exp);
        } else {
            throw new IllegalArgumentException(
                    "Cannot find expression or predicate in registry with ref: " + expression);
        }

        answer.init(getCamelContext());
        return answer;
    }

    protected Predicate createStaticPredicate(String expression) {
        Predicate answer;

        Object exp = getCamelContext().getRegistry().lookupByName(expression);
        if (exp instanceof Expression) {
            answer = ExpressionToPredicateAdapter.toPredicate((Expression) exp);
        } else if (exp instanceof Predicate) {
            answer = (Predicate) exp;
        } else {
            throw new IllegalArgumentException(
                    "Cannot find expression or predicate in registry with ref: " + expression);
        }

        answer.init(getCamelContext());
        return answer;
    }

    protected ExpressionAdapter createDynamic(final String expression) {
        ExpressionAdapter answer = new ExpressionAdapter() {

            private Expression exp;
            private Registry registry;

            @Override
            public void init(CamelContext context) {
                registry = context.getRegistry();
                exp = ExpressionBuilder.simpleExpression(expression);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Expression target = null;

                String ref = exp.evaluate(exchange, String.class);
                Object lookup = ref != null ? registry.lookupByName(ref) : null;

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
                            "Cannot find expression or predicate in registry with ref: " + ref);
                }
            }

            public String toString() {
                return "ref:" + exp.toString();
            }
        };

        answer.init(getCamelContext());
        return answer;
    }

}
