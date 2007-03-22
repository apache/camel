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
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;

/**
 * A builder of expressions or predicates based on values.
 *
 * @version $Revision: $
 */
public class ValueBuilder<E extends Exchange> {
    private Expression<E> expression;

    public ValueBuilder(Expression<E> expression) {
        this.expression = expression;
    }
    
    public Expression<E> getExpression() {
        return expression;
    }

    @Fluent
    public Predicate<E> isNotEqualTo(@FluentArg("value") Object value) {
        Expression<E> right = ExpressionBuilder.constantExpression(value);
        return PredicateBuilder.isNotEqualTo(expression, right);
    }

    @Fluent
    public Predicate<E> isEqualTo(@FluentArg("value") Object value) {
        Expression<E> right = ExpressionBuilder.constantExpression(value);
        return PredicateBuilder.isEqualTo(expression, right);
    }

    @Fluent
    public Predicate<E> isLessThan(@FluentArg("value") Object value) {
        Expression<E> right = ExpressionBuilder.constantExpression(value);
        return PredicateBuilder.isLessThan(expression, right);
    }

    @Fluent
    public Predicate<E> isLessThanOrEqualTo(@FluentArg("value") Object value) {
        Expression<E> right = ExpressionBuilder.constantExpression(value);
        return PredicateBuilder.isLessThanOrEqualTo(expression, right);
    }

    @Fluent
    public Predicate<E> isGreaterThan(@FluentArg("value") Object value) {
        Expression<E> right = ExpressionBuilder.constantExpression(value);
        return PredicateBuilder.isGreaterThan(expression, right);
    }

    @Fluent
    public Predicate<E> isGreaterThanOrEqualTo(@FluentArg("value") Object value) {
        Expression<E> right = ExpressionBuilder.constantExpression(value);
        return PredicateBuilder.isGreaterThanOrEqualTo(expression, right);
    }

    @Fluent
    public Predicate<E> isInstanceOf(@FluentArg("class") Class type) {
        return PredicateBuilder.isInstanceOf(expression, type);
    }

    @Fluent
    public Predicate<E> isNull() {
        return PredicateBuilder.isNull(expression);
    }

    @Fluent
    public Predicate<E> isNotNull() {
        return PredicateBuilder.isNotNull(expression);
    }



    @Fluent
    public ValueBuilder<E> tokenize() {
        return tokenize("\n");
    }

    @Fluent
    public ValueBuilder<E> tokenize(@FluentArg("token") String token) {
        Expression<E> newExp = ExpressionBuilder.tokenizeExpression(expression, token);
        return new ValueBuilder<E>(newExp);
    }
}
