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
    //-------------------------------------------------------------------------

    @Fluent
    public Predicate<E> isNotEqualTo(@FluentArg("value")Object value) {
        Expression<E> right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isNotEqualTo(expression, right));
    }

    @Fluent
    public Predicate<E> isEqualTo(@FluentArg("value")Object value) {
        Expression<E> right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isEqualTo(expression, right));
    }

    @Fluent
    public Predicate<E> isLessThan(@FluentArg("value")Object value) {
        Expression<E> right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isLessThan(expression, right));
    }

    @Fluent
    public Predicate<E> isLessThanOrEqualTo(@FluentArg("value")Object value) {
        Expression<E> right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isLessThanOrEqualTo(expression, right));
    }

    @Fluent
    public Predicate<E> isGreaterThan(@FluentArg("value")Object value) {
        Expression<E> right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isGreaterThan(expression, right));
    }

    @Fluent
    public Predicate<E> isGreaterThanOrEqualTo(@FluentArg("value")Object value) {
        Expression<E> right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isGreaterThanOrEqualTo(expression, right));
    }

    @Fluent
    public Predicate<E> isInstanceOf(@FluentArg("class")Class type) {
        return onNewPredicate(PredicateBuilder.isInstanceOf(expression, type));
    }

    @Fluent
    public Predicate<E> matchesRegex(@FluentArg("regex")String regex) {
        return onNewPredicate(PredicateBuilder.regex(expression, regex));
    }

    @Fluent
    public Predicate<E> isNull() {
        return onNewPredicate(PredicateBuilder.isNull(expression));
    }

    @Fluent
    public Predicate<E> isNotNull() {
        return onNewPredicate(PredicateBuilder.isNotNull(expression));
    }

    /**
     * Create a predicate that the left hand expression contains the value of the right hand expression
     *
     * @param value the element which is compared to be contained within this expression
     * @return a predicate which evaluates to true if the given value expression is contained within this
     * expression value
     */
    @Fluent
    public Predicate<E> contains(@FluentArg("value")Object value) {
        Expression<E> right = asExpression(value);
        return onNewPredicate(PredicateBuilder.contains(expression, right));
    }


    /**
     * Creates a predicate which is true if this expression matches the given regular expression
     *
     * @param regex the regular expression to match
     * @return a predicate which evaluates to true if the expression matches the regex
     */
    @Fluent
    public Predicate<E> regex(String regex) {
        return onNewPredicate(PredicateBuilder.regex(expression, regex));
    }


    // Expression builders
    //-------------------------------------------------------------------------

    @Fluent
    public ValueBuilder<E> tokenize() {
        return tokenize("\n");
    }

    @Fluent
    public ValueBuilder<E> tokenize(@FluentArg("token")String token) {
        Expression<E> newExp = ExpressionBuilder.tokenizeExpression(expression, token);
        return new ValueBuilder<E>(newExp);
    }

    /**
     * Tokenizes the string conversion of this expression using the given regular expression
     */
    @Fluent
    public ValueBuilder<E> regexTokenize(@FluentArg("regex")String regex) {
        Expression<E> newExp = ExpressionBuilder.regexTokenize(expression, regex);
        return new ValueBuilder<E>(newExp);
    }

    /**
     * Replaces all occurrencies of the regular expression with the given replacement
     */
    @Fluent
    public ValueBuilder<E> regexReplaceAll(@FluentArg("regex")String regex, @FluentArg("replacement")String replacement) {
        Expression<E> newExp = ExpressionBuilder.regexReplaceAll(expression, regex, replacement);
        return new ValueBuilder<E>(newExp);
    }

    /**
     * Replaces all occurrencies of the regular expression with the given replacement
     */
    @Fluent
    public ValueBuilder<E> regexReplaceAll(@FluentArg("regex")String regex, @FluentArg("replacement")Expression<E> replacement) {
        Expression<E> newExp = ExpressionBuilder.regexReplaceAll(expression, regex, replacement);
        return new ValueBuilder<E>(newExp);
    }


    /**
     * Converts the current value to the given type using the registered type converters
     *
     * @param type the type to convert the value to
     * @return the current builder
     */
    @Fluent
    public ValueBuilder<E> convertTo(@FluentArg("type")Class type) {
        Expression<E> newExp = ExpressionBuilder.convertTo(expression, type);
        return new ValueBuilder<E>(newExp);
    }

    /**
     * Converts the current value a String using the registered type converters
     *
     * @return the current builder
     */
    @Fluent
    public ValueBuilder<E> convertToString() {
        return convertTo(String.class);
    }

    /**
     * Appends the string evaluation of this expression with the given value
     * @param value the value or expression to append
     * @return the current builder
     */
    @Fluent
    public ValueBuilder<E> append(@FluentArg("value") Object value) {
        return new ValueBuilder<E>(ExpressionBuilder.append(expression, asExpression(value)));
    }

    
    // Implementation methods
    //-------------------------------------------------------------------------

    /**
     * A stategy method to allow derived classes to deal with the newly created predicate
     * in different ways
     */
    protected Predicate<E> onNewPredicate(Predicate<E> predicate) {
        return predicate;
    }

    protected Expression<E> asExpression(Object value) {
        if (value instanceof Expression) {
            return (Expression<E>) value;
        }
        else {
            return ExpressionBuilder.constantExpression(value);
        }
    }
}
