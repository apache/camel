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

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A {@link Collection} which aggregates exchanges together using a correlation
 * expression so that there is only a single message exchange sent for a single
 * correlation key.
 *
 * @version $Revision$
 */
public class AggregationCollection extends AbstractCollection<Exchange> {
    private static final transient Log LOG = LogFactory.getLog(AggregationCollection.class);
    private final Expression<Exchange> correlationExpression;
    private final AggregationStrategy aggregationStrategy;
    private Map<Object, Exchange> map = new LinkedHashMap<Object, Exchange>();

    public AggregationCollection(Expression<Exchange> correlationExpression,
                                 AggregationStrategy aggregationStrategy) {
        this.correlationExpression = correlationExpression;
        this.aggregationStrategy = aggregationStrategy;
    }

    protected Map<Object, Exchange> getMap() {
        return map;
    }

    @Override
    public boolean add(Exchange exchange) {
        Object correlationKey = correlationExpression.evaluate(exchange);
        Exchange oldExchange = map.get(correlationKey);
        Exchange newExchange = exchange;
        if (oldExchange != null) {
            newExchange = aggregationStrategy.aggregate(oldExchange, newExchange);
        }

        // the strategy may just update the old exchange and return it
        if (newExchange != oldExchange) {
            LOG.debug("put exchange:" + newExchange + " for key:"  + correlationKey);
            map.put(correlationKey, newExchange);
        }
        onAggregation(correlationKey, newExchange);
        return true;
    }

    public Iterator<Exchange> iterator() {
        return map.values().iterator();
    }

    public int size() {
        return map.size();
    }

    @Override
    public void clear() {
        map.clear();
    }

    /**
     * A strategy method allowing derived classes such as {@link PredicateAggregationCollection}
     * to check to see if the aggregation has completed
     */
    protected void onAggregation(Object correlationKey, Exchange newExchange) {
    }
}
