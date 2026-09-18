/*
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
package org.apache.camel.component.sjms.consumer;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.support.DefaultExchange;

public class BatchDefaultExchangeListAggregationStrategy implements AggregationStrategy {

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (oldExchange == null) {
            // As with the GroupedExchangeAggregationStrategy, for the first time we must create a new
            // empty exchange as the holder, as the outgoing exchange must not be one of the
            // grouped exchanges, as that causes a endless circular reference
            oldExchange = new DefaultExchange(newExchange);
            oldExchange.setProperty(ExchangePropertyKey.GROUPED_EXCHANGE, new ArrayList<Exchange>());
        }
        List<Exchange> list = oldExchange.getProperty(ExchangePropertyKey.GROUPED_EXCHANGE, List.class);
        list.add(newExchange);
        return oldExchange;
    }

    @Override
    public void onCompletion(Exchange exchange) {
        Object list = exchange.removeProperty(ExchangePropertyKey.GROUPED_EXCHANGE);
        if (list != null) {
            exchange.getIn().setBody(list);
        }
    }
}
