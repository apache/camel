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
package org.apache.camel.component.mock;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.StreamCache;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.builder.ExpressionClauseSupport;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.util.PredicateAssertHelper;

/**
 * A builder of assertions on message exchanges
 *
 * @version 
 */
public abstract class AssertionClause extends ExpressionClauseSupport<ValueBuilder> implements Runnable {

    protected final MockEndpoint mock;
    protected volatile int currentIndex;
    private final Set<Predicate> predicates = new LinkedHashSet<Predicate>();
    private final Expression previous = new PreviousTimestamp();
    private final Expression next = new NextTimestamp();

    public AssertionClause(MockEndpoint mock) {
        super(null);
        this.mock = mock;
    }

    // Builder methods
    // -------------------------------------------------------------------------

    public ValueBuilder expression(Expression expression) {
        // must override this method as we provide null in the constructor
        super.expression(expression);
        return new PredicateValueBuilder(getExpressionValue());
    }

    public ValueBuilder expression(ExpressionDefinition expression) {
        // must override this method as we provide null in the constructor
        super.expression(expression);
        return new PredicateValueBuilder(expression.createExpression(mock.getCamelContext()));
    }

    /**
     * Adds the given predicate to this assertion clause
     */
    public AssertionClause predicate(Predicate predicate) {
        addPredicate(predicate);
        return this;
    }

    /**
     * Adds the given predicate to this assertion clause
     */
    public ExpressionClause<AssertionClause> predicate() {
        ExpressionClause<AssertionClause> clause = new ExpressionClause<AssertionClause>(this);
        addPredicate(clause);
        return clause;
    }

    /**
     * Adds a {@link TimeClause} predicate for message arriving.
     */
    public TimeClause arrives() {
        final TimeClause clause = new TimeClause(previous, next);
        addPredicate(new Predicate() {
            public boolean matches(Exchange exchange) {
                return clause.matches(exchange);
            }

            @Override
            public String toString() {
                return "arrives " + clause.toString() + " exchange";
            }
        });
        return clause;
    }

    /**
     * Performs any assertions on the given exchange
     */
    protected void applyAssertionOn(MockEndpoint endpoint, int index, Exchange exchange) {
        for (Predicate predicate : predicates) {
            currentIndex = index;

            Object value = exchange.hasOut() ? exchange.getOut().getBody() : exchange.getIn().getBody();
            // if the value is StreamCache then ensure its readable before evaluating any predicates
            // by resetting it (this is also what StreamCachingAdvice does)
            if (value instanceof StreamCache) {
                ((StreamCache) value).reset();
            }

            PredicateAssertHelper.assertMatches(predicate, "Assertion error at index " + index + " on mock " + endpoint.getEndpointUri() + " with predicate: ", exchange);
        }
    }

    protected void addPredicate(Predicate predicate) {
        predicates.add(predicate);
    }

    @SuppressWarnings("unchecked")
    private final class PreviousTimestamp implements Expression {
        public <T> T evaluate(Exchange exchange, Class<T> type) {
            Date answer = null;
            if (currentIndex > 0 && mock.getReceivedCounter() > 0) {
                answer = mock.getReceivedExchanges().get(currentIndex - 1).getProperty(Exchange.RECEIVED_TIMESTAMP, Date.class);
            }
            return (T) answer;
        }
    }

    @SuppressWarnings("unchecked")
    private final class NextTimestamp implements Expression {
        public <T> T evaluate(Exchange exchange, Class<T> type) {
            Date answer = null;
            if (currentIndex < mock.getReceivedCounter() - 1) {
                answer = mock.getReceivedExchanges().get(currentIndex + 1).getProperty(Exchange.RECEIVED_TIMESTAMP, Date.class);
            }
            return (T) answer;
        }
    }

    /**
     * Public class needed for fluent builders
     */
    public final class PredicateValueBuilder extends ValueBuilder {

        public PredicateValueBuilder(Expression expression) {
            super(expression);
        }

        @Override
        protected Predicate onNewPredicate(Predicate predicate) {
            predicate = super.onNewPredicate(predicate);
            addPredicate(predicate);
            return predicate;
        }

        @Override
        protected ValueBuilder onNewValueBuilder(Expression exp) {
            return new PredicateValueBuilder(exp);
        }
    }
}
