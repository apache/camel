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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;

/**
 * An aggregator collection which uses a predicate to decide when an aggregation is completed for
 * a particular correlation key
 *
 * @version $Revision$
 */
public class PredicateAggregationCollection extends AggregationCollection {
    private Predicate aggregationCompletedPredicate;
    private List<Exchange> collection = new ArrayList<Exchange>();

    public PredicateAggregationCollection(Expression<Exchange> correlationExpression, AggregationStrategy aggregationStrategy, Predicate aggregationCompletedPredicate) {
        super(correlationExpression, aggregationStrategy);
        this.aggregationCompletedPredicate = aggregationCompletedPredicate;
    }

    @Override
    protected void onAggregation(Object correlationKey, Exchange newExchange) {
        if (aggregationCompletedPredicate.matches(newExchange)) {
            // this exchange has now aggregated so lets add it to the collection of things
            // to send
            super.getMap().remove(correlationKey);
            collection.add(newExchange);
        }
    }

    @Override
    public Iterator<Exchange> iterator() {
        return collection.iterator();
    }

    @Override
    public int size() {
        return collection.size();
    }

    @Override
    public void clear() {
        collection.clear();
        super.clear();
    }
}
