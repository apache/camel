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
package org.apache.camel.processor.aggregate;

import java.util.Collection;
import java.util.Iterator;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;

/**
 * A {@link Collection} which aggregates exchanges together,
 * using a correlation {@link Expression} and a {@link AggregationStrategy}.
 * <p/>
 * The Default Implementation will group messages based on the correlation expression.
 * Other implementations could for instance just add all exchanges as a batch.
 *
 * @version $Revision$
 */
public interface AggregationCollection extends Collection<Exchange> {

    /**
     * Gets the correlation expression
     */
    Expression<Exchange> getCorrelationExpression();

    /**
     * Sets the correlation expression to be used
     */
    void setCorrelationExpression(Expression<Exchange> correlationExpression);

    /**
     * Gets the aggregation strategy
     */
    AggregationStrategy getAggregationStrategy();

    /**
     * Sets the aggregation strategy to be used
     */
    void setAggregationStrategy(AggregationStrategy aggregationStrategy);

    /**
     * Adds the given exchange to this collection
     */
    boolean add(Exchange exchange);

    /**
     * Gets the iterator to iterate this collection.
     */
    Iterator<Exchange> iterator();

    /**
     * Gets the size of this collection
     */
    int size();

    /**
     * Clears this colleciton
     */
    void clear();

    /**
     * A strategy method allowing derived classes such as {@link PredicateAggregationCollection}
     * to check to see if the aggregation has completed
     */
    void onAggregation(Object correlationKey, Exchange newExchange);

}
