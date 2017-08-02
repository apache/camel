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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.util.ExpressionToPredicateAdapter;

/**
 * A builder of expressions or predicates based on values.
 * 
 * @version 
 */
public class ValueBuilder implements Expression, Predicate {
    private Expression expression;
    private boolean not;

    public ValueBuilder(Expression expression) {
        this.expression = expression;
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        return expression.evaluate(exchange, type);
    }

    @Override
    public boolean matches(Exchange exchange) {
        return PredicateBuilder.toPredicate(getExpression()).matches(exchange);
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return expression.toString();
    }

    // Predicate builders
    // -------------------------------------------------------------------------

    public Predicate matches(Expression expression) {
        return onNewPredicate(ExpressionToPredicateAdapter.toPredicate(expression));
    }

    public ExpressionClause<Predicate> matches() {
        return new ExpressionClause<Predicate>(onNewPredicate(ExpressionToPredicateAdapter.toPredicate(expression))); 
    }

    public Predicate isNotEqualTo(Object value) {
        Expression right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isNotEqualTo(expression, right));
    }

    public Predicate isEqualTo(Object value) {
        Expression right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isEqualTo(expression, right));
    }

    public Predicate isEqualToIgnoreCase(Object value) {
        Expression right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isEqualToIgnoreCase(expression, right));
    }

    public Predicate isLessThan(Object value) {
        Expression right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isLessThan(expression, right));
    }

    public Predicate isLessThanOrEqualTo(Object value) {
        Expression right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isLessThanOrEqualTo(expression, right));
    }

    public Predicate isGreaterThan(Object value) {
        Expression right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isGreaterThan(expression, right));
    }

    public Predicate isGreaterThanOrEqualTo(Object value) {
        Expression right = asExpression(value);
        return onNewPredicate(PredicateBuilder.isGreaterThanOrEqualTo(expression, right));
    }

    public Predicate isInstanceOf(Class<?> type) {
        return onNewPredicate(PredicateBuilder.isInstanceOf(expression, type));
    }

    public Predicate isNull() {
        return onNewPredicate(PredicateBuilder.isNull(expression));
    }

    public Predicate isNotNull() {
        return onNewPredicate(PredicateBuilder.isNotNull(expression));
    }
   
    public Predicate not(Predicate predicate) {
        return onNewPredicate(PredicateBuilder.not(predicate));
    }

    public Predicate in(Object... values) {
        List<Predicate> predicates = new ArrayList<Predicate>();
        for (Object value : values) {
            Expression right = asExpression(value);
            right = ExpressionBuilder.convertToExpression(right, expression);
            Predicate predicate = onNewPredicate(PredicateBuilder.isEqualTo(expression, right));
            predicates.add(predicate);
        }
        return in(predicates.toArray(new Predicate[predicates.size()]));
    }

    public Predicate in(Predicate... predicates) {
        return onNewPredicate(PredicateBuilder.in(predicates));
    }

    public Predicate startsWith(Object value) {
        Expression right = asExpression(value);
        return onNewPredicate(PredicateBuilder.startsWith(expression, right));
    }

    public Predicate endsWith(Object value) {
        Expression right = asExpression(value);
        return onNewPredicate(PredicateBuilder.endsWith(expression, right));
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
    public Predicate contains(Object value) {
        Expression right = asExpression(value);
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
    public Predicate regex(String regex) {
        return onNewPredicate(PredicateBuilder.regex(expression, regex));
    }

    // Expression builders
    // -------------------------------------------------------------------------

    public ValueBuilder tokenize() {
        return tokenize("\n");
    }

    public ValueBuilder tokenize(String token) {
        Expression newExp = ExpressionBuilder.tokenizeExpression(expression, token);
        return onNewValueBuilder(newExp);
    }

    public ValueBuilder tokenize(String token, int group, boolean skipFirst) {
        return tokenize(token, "" + group, skipFirst);
    }

    public ValueBuilder tokenize(String token, String group, boolean skipFirst) {
        Expression newExp = ExpressionBuilder.tokenizeExpression(expression, token);
        if (group == null && skipFirst) {
            // wrap in skip first (if group then it has its own skip first logic)
            newExp = ExpressionBuilder.skipFirstExpression(newExp);
        }
        newExp = ExpressionBuilder.groupIteratorExpression(newExp, token, group, skipFirst);
        return onNewValueBuilder(newExp);
    }

    public ValueBuilder tokenizeXML(String tagName, String inheritNamespaceTagName) {
        Expression newExp = ExpressionBuilder.tokenizeXMLExpression(tagName, inheritNamespaceTagName);
        return onNewValueBuilder(newExp);
    }

    public ValueBuilder xtokenize(String path, Namespaces namespaces) {
        return xtokenize(path, 'i', namespaces);
    }

    public ValueBuilder xtokenize(String path, char mode, Namespaces namespaces) {
        Expression newExp = ExpressionBuilder.tokenizeXMLAwareExpression(path, mode);
        ((NamespaceAware)newExp).setNamespaces(namespaces.getNamespaces());
        return onNewValueBuilder(newExp);
    }

    public ValueBuilder tokenizePair(String startToken, String endToken, boolean includeTokens) {
        Expression newExp = ExpressionBuilder.tokenizePairExpression(startToken, endToken, includeTokens);
        return onNewValueBuilder(newExp);
    }

    /**
     * Tokenizes the string conversion of this expression using the given
     * regular expression
     */
    public ValueBuilder regexTokenize(String regex) {
        Expression newExp = ExpressionBuilder.regexTokenizeExpression(expression, regex);
        return onNewValueBuilder(newExp);
    }

    /**
     * Replaces all occurrences of the regular expression with the given
     * replacement
     */
    public ValueBuilder regexReplaceAll(String regex, String replacement) {
        Expression newExp = ExpressionBuilder.regexReplaceAll(expression, regex, replacement);
        return onNewValueBuilder(newExp);
    }

    /**
     * Replaces all occurrences of the regular expression with the given
     * replacement
     */
    public ValueBuilder regexReplaceAll(String regex, Expression replacement) {
        Expression newExp = ExpressionBuilder.regexReplaceAll(expression, regex, replacement);
        return onNewValueBuilder(newExp);
    }

    /**
     * Converts the current value to the given type using the registered type
     * converters
     * 
     * @param type the type to convert the value to
     * @return the current builder
     */
    public ValueBuilder convertTo(Class<?> type) {
        Expression newExp = ExpressionBuilder.convertToExpression(expression, type);
        return onNewValueBuilder(newExp);
    }

    /**
     * Converts the current value to a String using the registered type converters
     * 
     * @return the current builder
     */
    public ValueBuilder convertToString() {
        return convertTo(String.class);
    }

    /**
     * Appends the string evaluation of this expression with the given value
     *
     * @param value the value or expression to append
     * @return the current builder
     */
    public ValueBuilder append(Object value) {
        Expression newExp = ExpressionBuilder.append(expression, asExpression(value));
        return onNewValueBuilder(newExp);
    }

    /**
     * Prepends the string evaluation of this expression with the given value
     *
     * @param value the value or expression to prepend
     * @return the current builder
     */
    public ValueBuilder prepend(Object value) {
        Expression newExp = ExpressionBuilder.prepend(expression, asExpression(value));
        return onNewValueBuilder(newExp);
    }

    /**
     * Sorts the current value using the given comparator. The current value must be convertable
     * to a {@link List} to allow sorting using the comparator.
     *
     * @param comparator  the comparator used by sorting
     * @return the current builder
     */
    public ValueBuilder sort(Comparator<?> comparator) {
        Expression newExp = ExpressionBuilder.sortExpression(expression, comparator);
        return onNewValueBuilder(newExp);
    }

    /**
     * Invokes the method with the given name (supports OGNL syntax).
     *
     * @param methodName  name of method to invoke.
     * @return the current builder
     */
    public ValueBuilder method(String methodName) {
        Expression newExp = ExpressionBuilder.ognlExpression(expression, methodName);
        return onNewValueBuilder(newExp);
    }

    /**
     * Negates the built expression.
     *
     * @return the current builder
     */
    public ValueBuilder not() {
        not = true;
        return this;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * A strategy method to allow derived classes to deal with the newly created
     * predicate in different ways
     */
    protected Predicate onNewPredicate(Predicate predicate) {
        if (not) {
            return PredicateBuilder.not(predicate);
        } else {
            return predicate;
        }
    }

    protected Expression asExpression(Object value) {
        if (value instanceof Expression) {
            return (Expression)value;
        } else {
            return ExpressionBuilder.constantExpression(value);
        }
    }

    protected ValueBuilder onNewValueBuilder(Expression exp) {
        return new ValueBuilder(exp);
    }
}
