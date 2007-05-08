/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mock;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import static org.apache.camel.builder.ExpressionBuilder.bodyExpression;
import static org.apache.camel.builder.ExpressionBuilder.headerExpression;
import org.apache.camel.builder.Fluent;
import org.apache.camel.builder.FluentArg;
import org.apache.camel.builder.ValueBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * A builder of assertions on message exchanges
 *
 * @version $Revision: 1.1 $
 */
public abstract class AssertionClause<E extends Exchange> implements Runnable {

    private List<Predicate<E>> predicates = new ArrayList<Predicate<E>>();

    // Builder methods
    //-------------------------------------------------------------------------

    /**
     * Adds the given predicate to this assertion clause
     */
    public AssertionClause<E> predicate(Predicate<E> predicate) {
        addPredicate(predicate);
        return this;
    }

    /**
     * Returns a predicate and value builder for headers on an exchange
     */
    @Fluent
    public ValueBuilder<E> header(@FluentArg("name")String name) {
        Expression<E> expression = headerExpression(name);
        return new PredicateValueBuilder(expression);
    }

    /**
     * Returns a predicate and value builder for the inbound body on an exchange
     */
    @Fluent
    public PredicateValueBuilder body() {
        Expression<E> expression = bodyExpression();
        return new PredicateValueBuilder(expression);
    }

    /**
     * Returns a predicate and value builder for the inbound message body as a specific type
     */
    @Fluent
    public <T> PredicateValueBuilder bodyAs(@FluentArg("class")Class<T> type) {
        Expression<E> expression = bodyExpression(type);
        return new PredicateValueBuilder(expression);
    }

    /**
     * Returns a predicate and value builder for the outbound body on an exchange
     */
    @Fluent
    public PredicateValueBuilder outBody() {
        Expression<E> expression = bodyExpression();
        return new PredicateValueBuilder(expression);
    }

    /**
     * Returns a predicate and value builder for the outbound message body as a specific type
     */
    @Fluent
    public <T> PredicateValueBuilder outBody(@FluentArg("class")Class<T> type) {
        Expression<E> expression = bodyExpression(type);
        return new PredicateValueBuilder(expression);
    }

    /**
     * Performs any assertions on the given exchange
     */
    protected void applyAssertionOn(MockEndpoint endpoint, int index, E exchange) {
        for (Predicate<E> predicate : predicates) {
            predicate.assertMatches(endpoint.getEndpointUri() + " ", exchange);
        }
    }

    protected void addPredicate(Predicate<E> predicate) {
        predicates.add(predicate);
    }

    public class PredicateValueBuilder extends ValueBuilder<E> {

        public PredicateValueBuilder(Expression<E> expression) {
            super(expression);
        }

        protected Predicate<E> onNewPredicate(Predicate<E> predicate) {
            addPredicate(predicate);
            return predicate;
        }
    }
}
