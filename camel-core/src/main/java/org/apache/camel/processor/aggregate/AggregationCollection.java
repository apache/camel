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

/**
 * A {@link Collection} which aggregates exchanges together using a correlation
 * expression so that there is only a single message exchange sent for a single
 * correlation key.
 * 
 * @version $Revision: 1.1 $
 */
public class AggregationCollection extends AbstractCollection<Exchange> {
    private final Expression<Exchange> correlationExpression;
    private final AggregationStrategy aggregationStrategy;
    private Map<Object, Exchange> map = new LinkedHashMap<Object, Exchange>();

    public AggregationCollection(Expression<Exchange> correlationExpression,
                                 AggregationStrategy aggregationStrategy) {
        this.correlationExpression = correlationExpression;
        this.aggregationStrategy = aggregationStrategy;
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
            map.put(correlationKey, newExchange);
        }
        return true;
    }

    public Iterator<Exchange> iterator() {
        return map.values().iterator();
    }

    public int size() {
        return map.size();
    }
}
