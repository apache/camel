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
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;

/**
 * A builder of expressions or predicates based on values.
 * 
 * @version $Revision$
 */
public class ValueBuilder<E extends Exchange> implements Expression<E> {
    private Expression<E> expression;

    public ValueBuilder(Expression<E> expression) {
        this.expression = expression;
    }

    public Object evaluate(E exchange) {
        return expression.evaluate(exchange);
    }

    public Expression<E> getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return expression.toString();
    }

    // Predicate builders
    // -------------------------------------------------------------------------

    public Predicate<E> isNotEqualTo(Object value) {
        Expression<E> right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isNotEqualTo(expression, right));
    }

    public Predicate<E> isEqualTo(Object value) {
        Expression<E> right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isEqualTo(expression, right));
    }

    public Predicate<E> isLessThan(Object value) {
        Expression<E> right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isLessThan(expression, right));
    }

    public Predicate<E> isLessThanOrEqualTo(Object value) {
        Expression<E> right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isLessThanOrEqualTo(expression, right));
    }

    public Predicate<E> isGreaterThan(Object value) {
        Expression<E> right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isGreaterThan(expression, right));
    }

    public Predicate<E> isGreaterThanOrEqualTo(Object value) {
        Expression<E> right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isGreaterThanOrEqualTo(expression, right));
    }

    public Predicate<E> isInstanceOf(Class type) {
        return onNewPredicate(PredicateBuilder.isInstanceOf(expression, type));
    }

    /**
     * @deprecated use {@link #regex(String)}. Will be removed in Camel 2.0
     */
    public Predicate<E> matchesRegex(String regex) {
        return onNewPredicate(PredicateBuilder.regex(expression, regex));
    }

    public Predicate<E> isNull() {
        return onNewPredicate(PredicateBuilder.isNull(expression));
    }

    public Predicate<E> isNotNull() {
        return onNewPredicate(PredicateBuilder.isNotNull(expression));
    }

    /**
     * Create a predicate that the left hand expression contains the value of
     * the right hand expression
     * 
     * @param value the element which is compared to be contained within this
     *                expression
     * @return a predicate which evaluates to true if the given value expression
     *         is contained within this expression value
     */
    public Predicate<E> contains(Object value) {
        Expression<E> right = asExpression(value);
        return onNewPredicate(PredicateBuilder.contains(expression, right));
    }

    /**
     * Creates a predicate which is true if this expression matches the given
     * regular expression
     * 
     * @param regex the regular expression to match
     * @return a predicate which evaluates to true if the expression matches the
     *         regex
     */
    public Predicate<E> regex(String regex) {
        return onNewPredicate(PredicateBuilder.regex(expression, regex));
    }

    // Expression builders
    // -------------------------------------------------------------------------

    public ValueBuilder<E> tokenize() {
        return tokenize("\n");
    }

    public ValueBuilder<E> tokenize(String token) {
        Expression<E> newExp = ExpressionBuilder.tokenizeExpression(expression, token);
        return new ValueBuilder<E>(newExp);
    }

    /**
     * Tokenizes the string conversion of this expression using the given
     * regular expression
     */
    public ValueBuilder<E> regexTokenize(String regex) {
        Expression<E> newExp = ExpressionBuilder.regexTokenize(expression, regex);
        return new ValueBuilder<E>(newExp);
    }

    /**
     * Replaces all occurrencies of the regular expression with the given
     * replacement
     */
    public ValueBuilder<E> regexReplaceAll(String regex, String replacement) {
        Expression<E> newExp = ExpressionBuilder.regexReplaceAll(expression, regex, replacement);
        return new ValueBuilder<E>(newExp);
    }

    /**
     * Replaces all occurrencies of the regular expression with the given
     * replacement
     */
    public ValueBuilder<E> regexReplaceAll(String regex, Expression<E> replacement) {
        Expression<E> newExp = ExpressionBuilder.regexReplaceAll(expression, regex, replacement);
        return new ValueBuilder<E>(newExp);
    }

    /**
     * Converts the current value to the given type using the registered type
     * converters
     * 
     * @param type the type to convert the value to
     * @return the current builder
     */
    public ValueBuilder<E> convertTo(Class type) {
        Expression<E> newExp = ExpressionBuilder.convertTo(expression, type);
        return new ValueBuilder<E>(newExp);
    }

    /**
     * Converts the current value a String using the registered type converters
     * 
     * @return the current builder
     */
    public ValueBuilder<E> convertToString() {
        return convertTo(String.class);
    }

    /**
     * Appends the string evaluation of this expression with the given value
     * 
     * @param value the value or expression to append
     * @return the current builder
     */
    public ValueBuilder<E> append(Object value) {
        return new ValueBuilder<E>(ExpressionBuilder.append(expression, asExpression(value)));
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * A strategy method to allow derived classes to deal with the newly created
     * predicate in different ways
     */
    protected Predicate<E> onNewPredicate(Predicate<E> predicate) {
        return predicate;
    }

    protected Expression<E> asExpression(Object value) {
        if (value instanceof Expression) {
            return (Expression<E>)value;
        } else {
            return ExpressionBuilder.constantExpression(value);
        }
    }
}
