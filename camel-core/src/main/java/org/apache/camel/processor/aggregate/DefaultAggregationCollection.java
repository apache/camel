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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A {@link java.util.Collection} which aggregates exchanges together using a correlation
 * expression so that there is only a single message exchange sent for a single
 * correlation key.
 *
 * @version $Revision$
 */
public class DefaultAggregationCollection extends AbstractCollection<Exchange> implements AggregationCollection {

    private static final transient Log LOG = LogFactory.getLog(DefaultAggregationCollection.class);
    private Expression correlationExpression;
    private AggregationStrategy aggregationStrategy;
    private final Map<Object, Exchange> aggregated = new LinkedHashMap<Object, Exchange>();

    public DefaultAggregationCollection() {
    }

    public DefaultAggregationCollection(Expression correlationExpression, AggregationStrategy aggregationStrategy) {
        this.correlationExpression = correlationExpression;
        this.aggregationStrategy = aggregationStrategy;
    }

    protected Map<Object, Exchange> getAggregated() {
        return aggregated;
    }

    @Override
    public boolean add(Exchange exchange) {
        Object correlationKey = correlationExpression.evaluate(exchange);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Evaluated expression: " + correlationExpression + " as CorrelationKey: " + correlationKey);
        }
        Exchange oldExchange = aggregated.get(correlationKey);
        Exchange newExchange = exchange;

        if (oldExchange != null) {
            Integer count = oldExchange.getProperty(Exchange.AGGREGATED_SIZE, Integer.class);
            if (count == null) {
                count = 1;
            }
            count++;
            newExchange = aggregationStrategy.aggregate(oldExchange, newExchange);
            newExchange.setProperty(Exchange.AGGREGATED_SIZE, count);
        }

        // the strategy may just update the old exchange and return it
        if (!newExchange.equals(oldExchange)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Put exchange:" + newExchange + " with coorelation key:"  + correlationKey);
            }
            if (oldExchange == null) {
                newExchange.setProperty(Exchange.AGGREGATED_SIZE, Integer.valueOf(1));
            }
            aggregated.put(correlationKey, newExchange);
        }

        onAggregation(correlationKey, newExchange);

        return true;
    }

    public Iterator<Exchange> iterator() {
        return aggregated.values().iterator();
    }

    public int size() {
        return aggregated.size();
    }

    @Override
    public void clear() {
        aggregated.clear();
    }

    public void onAggregation(Object correlationKey, Exchange newExchange) {
    }

    public Expression getCorrelationExpression() {
        return correlationExpression;
    }

    public void setCorrelationExpression(Expression correlationExpression) {
        this.correlationExpression = correlationExpression;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }
}
