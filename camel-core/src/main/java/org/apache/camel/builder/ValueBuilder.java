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

    public Predicate<E> isNotEqualTo(Object value) {
        Expression<E> right = ExpressionBuilder.constantExpression(value);
        return PredicateBuilder.isNotEqualTo(expression, right);
    }

    public Predicate<E> isEqualTo(Object value) {
        Expression<E> right = ExpressionBuilder.constantExpression(value);
        return PredicateBuilder.isEqualTo(expression, right);
    }

    public Predicate<E> isLessThan(Object value) {
        Expression<E> right = ExpressionBuilder.constantExpression(value);
        return PredicateBuilder.isLessThan(expression, right);
    }

    public Predicate<E> isLessThanOrEqualTo(Object value) {
        Expression<E> right = ExpressionBuilder.constantExpression(value);
        return PredicateBuilder.isLessThanOrEqualTo(expression, right);
    }

    public Predicate<E> isGreaterThan(Object value) {
        Expression<E> right = ExpressionBuilder.constantExpression(value);
        return PredicateBuilder.isGreaterThan(expression, right);
    }

    public Predicate<E> isGreaterThanOrEqualTo(Object value) {
        Expression<E> right = ExpressionBuilder.constantExpression(value);
        return PredicateBuilder.isGreaterThanOrEqualTo(expression, right);
    }

    public Expression<E> getExpression() {
        return expression;
    }
}
