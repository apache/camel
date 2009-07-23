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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
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
    private final AtomicInteger counter = new AtomicInteger();

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
        // do not add exchange if it was filtered
        Boolean filtered = exchange.getProperty(Exchange.FILTERED, Boolean.class);
        if (filtered != null && filtered) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Cannot aggregate exchange as its filtered: " + exchange);
            }
            return false;
        }

        Object correlationKey = correlationExpression.evaluate(exchange, Object.class);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Evaluated expression: " + correlationExpression + " as correlation key: " + correlationKey);
        }

        // TODO: correlationKey evaluated to null should be skipped by default

        Exchange oldExchange = aggregated.get(correlationKey);
        Exchange newExchange = exchange;

        Integer size = 1;
        if (oldExchange != null) {
            size = oldExchange.getProperty(Exchange.AGGREGATED_SIZE, Integer.class);
            ObjectHelper.notNull(size, Exchange.AGGREGATED_SIZE + " on " + oldExchange);
            size++;
        }

        // prepare the exchanges for aggregation
        ExchangeHelper.prepareAggregation(oldExchange, newExchange);
        newExchange = aggregationStrategy.aggregate(oldExchange, newExchange);
        newExchange.setProperty(Exchange.AGGREGATED_SIZE, size);

        // update the index counter
        newExchange.setProperty(Exchange.AGGREGATED_INDEX, counter.getAndIncrement());

        // the strategy may just update the old exchange and return it
        if (!newExchange.equals(oldExchange)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Put exchange:" + newExchange + " with correlation key:"  + correlationKey);
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
        counter.set(0);
    }

    public void onAggregation(Object correlationKey, Exchange exchange) {
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
