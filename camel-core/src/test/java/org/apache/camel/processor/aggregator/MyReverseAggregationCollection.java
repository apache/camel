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

package org.apache.camel.processor.aggregator;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.processor.aggregate.AggregationCollection;
import org.apache.camel.processor.aggregate.AggregationStrategy;

//START SNIPPET: e1
class MyReverseAggregationCollection extends AbstractCollection<Exchange> implements AggregationCollection {

    private List<Exchange> collection = new ArrayList<Exchange>();
    private Expression correlation;
    private AggregationStrategy strategy;

    public Expression getCorrelationExpression() {
        return correlation;
    }

    public void setCorrelationExpression(Expression correlationExpression) {
        this.correlation = correlationExpression;
    }

    public AggregationStrategy getAggregationStrategy() {
        return strategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.strategy = aggregationStrategy;
    }

    public boolean add(Exchange exchange) {
        return collection.add(exchange);
    }

    public Iterator<Exchange> iterator() {
        // demonstrate the we can do something with this collection, so we reverse it
        Collections.reverse(collection);

        return collection.iterator();
    }

    public int size() {
        return collection.size();
    }

    public void clear() {
        collection.clear();
    }

    public void onAggregation(Object correlationKey, Exchange newExchange) {
        add(newExchange);
    }
}
//END SNIPPET: e1