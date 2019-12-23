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
package org.apache.camel.processor.aggregate;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;

/**
 * Aggregate all exchanges into a {@link List} of values defined by the {@link #getValue(Exchange)} call.
 * The combined Exchange will hold all the aggregated exchanges in a {@link java.util.List}
 * as a exchange property with the key {@link org.apache.camel.Exchange#GROUPED_EXCHANGE}.
 * <p/>
 * The method {@link #isStoreAsBodyOnCompletion()} determines if the aggregated {@link List} should
 * be stored on the {@link org.apache.camel.Message#setBody(Object)} or be kept as a property
 * on the exchange.
 * <br/>
 * The default behavior to store as message body, allows to more easily group together a list of values
 * and have its result stored as a {@link List} on the completed {@link Exchange}.
 *
 * @since 2.11
 */
public abstract class AbstractListAggregationStrategy<V> implements AggregationStrategy {

    /**
     * This method is implemented by the sub-class and is called to retrieve
     * an instance of the value that will be aggregated and forwarded to the
     * receiving end point.
     * <p/>
     * If <tt>null</tt> is returned, then the value is <b>not</b> added to the {@link List}.
     *
     * @param exchange  The exchange that is used to retrieve the value from
     * @return An instance of V that is the associated value of the passed exchange
     */
    public abstract V getValue(Exchange exchange);

    /**
     * Whether to store the completed aggregated {@link List} as message body, or to keep as property on the exchange.
     * <p/>
     * The default behavior is <tt>true</tt> to store as message body.
     *
     * @return <tt>true</tt> to store as message body, <tt>false</tt> to keep as property on the exchange.
     */
    public boolean isStoreAsBodyOnCompletion() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onCompletion(Exchange exchange) {
        if (exchange != null && isStoreAsBodyOnCompletion()) {
            List<V> list = (List<V>) exchange.removeProperty(Exchange.GROUPED_EXCHANGE);
            if (list != null) {
                exchange.getIn().setBody(list);
            }
        }
    }

    /**
     * This method will aggregate the old and new exchange and return the result.
     *
     * @param oldExchange The oldest exchange, can be null
     * @param newExchange The newest exchange, can be null
     * @return a composite exchange of the old and/or new exchanges
     */
    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        List<V> list;

        if (oldExchange == null) {
            list = getList(newExchange);
        } else {
            list = getList(oldExchange);
        }

        if (newExchange != null) {
            V value = getValue(newExchange);
            if (value != null) {
                list.add(value);
            }
        }

        return oldExchange != null ? oldExchange : newExchange;
    }

    @SuppressWarnings("unchecked")
    private List<V> getList(Exchange exchange) {
        List<V> list = exchange.getProperty(Exchange.GROUPED_EXCHANGE, List.class);
        if (list == null) {
            list = new GroupedExchangeList<>();
            exchange.setProperty(Exchange.GROUPED_EXCHANGE, list);
        }
        return list;
    }

    /**
     * A list to contains grouped {@link Exchange}s.
     */
    private static final class GroupedExchangeList<E> extends ArrayList<E> {

        private static final long serialVersionUID = 1L;

        @Override
        public String toString() {
            // lets override toString so we don't write data for all the Exchanges by default
            return "List<Exchange>(" + size() + " elements)";
        }
    }

}
