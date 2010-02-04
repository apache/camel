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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.builder.ExpressionClauseSupport;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.util.PredicateAssertHelper;

/**
 * A builder of assertions on message exchanges
 *
 * @version $Revision$
 */
public abstract class AssertionClause extends ExpressionClauseSupport<ValueBuilder> implements Runnable {

    private List<Predicate> predicates = new ArrayList<Predicate>();

    public AssertionClause() {
        super(null);
    }

    // Builder methods
    // -------------------------------------------------------------------------
    public ValueBuilder expression(Expression expression) {
        super.expression(expression);
        return new PredicateValueBuilder(getExpressionValue());
    }

    /**
     * Adds the given predicate to this assertion clause
     */
    public AssertionClause predicate(Predicate predicate) {
        addPredicate(predicate);
        return this;
    }

    public ExpressionClause<AssertionClause> predicate() {
        ExpressionClause<AssertionClause> clause = new ExpressionClause<AssertionClause>(this);
        addPredicate(clause);
        return clause;
    }

    /**
     * Performs any assertions on the given exchange
     */
    protected void applyAssertionOn(MockEndpoint endpoint, int index, Exchange exchange) {
        for (Predicate predicate : predicates) {
            PredicateAssertHelper.assertMatches(predicate, endpoint.getEndpointUri() + " ", exchange);
        }
    }

    protected void addPredicate(Predicate predicate) {
        predicates.add(predicate);
    }

    /**
     * Public class needed for fluent builders
     */
    public final class PredicateValueBuilder extends ValueBuilder {

        public PredicateValueBuilder(Expression expression) {
            super(expression);
        }

        protected Predicate onNewPredicate(Predicate predicate) {
            addPredicate(predicate);
            return predicate;
        }
    }
}
