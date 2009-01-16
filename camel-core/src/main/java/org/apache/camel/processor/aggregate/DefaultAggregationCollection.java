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
public class DefaultAggregationCollection extends AbstractCollection<Exchange> implements AggregationCollection {

    private static final transient Log LOG = LogFactory.getLog(DefaultAggregationCollection.class);
    private Expression<Exchange> correlationExpression;
    private AggregationStrategy aggregationStrategy;
    private Map<Object, Exchange> map = new LinkedHashMap<Object, Exchange>();

    public DefaultAggregationCollection() {
    }

    public DefaultAggregationCollection(Expression<Exchange> correlationExpression, AggregationStrategy aggregationStrategy) {
        this.correlationExpression = correlationExpression;
        this.aggregationStrategy = aggregationStrategy;
    }

    protected Map<Object, Exchange> getMap() {
        return map;
    }

    @Override
    public boolean add(Exchange exchange) {
        Object correlationKey = correlationExpression.evaluate(exchange);
        if (LOG.isDebugEnabled()) {
            LOG.debug("evaluated expression: " + correlationExpression + " as CorrelationKey: " + correlationKey);
        }
        Exchange oldExchange = map.get(correlationKey);
        Exchange newExchange = exchange;

        if (oldExchange != null) {
            Integer count = oldExchange.getProperty(Exchange.AGGREGATED_COUNT, Integer.class);
            if (count == null) {
                count = 1;
            }
            count++;
            newExchange = aggregationStrategy.aggregate(oldExchange, newExchange);
            newExchange.setProperty(Exchange.AGGREGATED_COUNT, count);
        }

        // the strategy may just update the old exchange and return it
        if (newExchange != oldExchange) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("put exchange:" + newExchange + " for key:"  + correlationKey);
            }
            if (oldExchange == null) {
                newExchange.setProperty(Exchange.AGGREGATED_COUNT, Integer.valueOf(1));
            }
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

    public void onAggregation(Object correlationKey, Exchange newExchange) {
    }

    public Expression<Exchange> getCorrelationExpression() {
        return correlationExpression;
    }

    public void setCorrelationExpression(Expression<Exchange> correlationExpression) {
        this.correlationExpression = correlationExpression;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }
}
