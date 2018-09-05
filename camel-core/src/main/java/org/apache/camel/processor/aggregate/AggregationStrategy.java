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

import org.apache.camel.Exchange;

/**
 * A strategy for aggregating two exchanges together into a single exchange.
 * <p/>
 * On the first invocation of the {@link #aggregate(org.apache.camel.Exchange, org.apache.camel.Exchange) aggregate}
 * method the <tt>oldExchange</tt> parameter is <tt>null</tt>. The reason is that we have not aggregated anything yet.
 * So its only the <tt>newExchange</tt> that has a value. Usually you just return the <tt>newExchange</tt> in this
 * situation. But you still have the power to decide what to do, for example you can do some alternation on the exchange
 * or remove some headers. And a more common use case is for instance to count some values from the body payload. That
 * could be to sum up a total amount etc.
 * <p/>
 * Note that <tt>oldExchange</tt> may be <tt>null</tt> more than once when this strategy is throwing a {@link java.lang.RuntimeException}
 * and <tt>parallelProcessing</tt> is used. You can work around this behavior using the <tt>stopOnAggregateException</tt> option.
 * <p/>
 * It is possible that <tt>newExchange</tt> is <tt>null</tt> which could happen if there was no data possible
 * to acquire. Such as when using a {@link org.apache.camel.processor.PollEnricher} to poll from a JMS queue which
 * is empty and a timeout was set.
 * <p/>
 * Possible implementations include performing some kind of combining or delta processing, such as adding line items
 * together into an invoice or just using the newest exchange and removing old exchanges such as for state tracking or
 * market data prices; where old values are of little use.
 * <p/>
 * If an implementation also implements {@link org.apache.camel.Service} then any <a href="http://camel.apache.org/eip">EIP</a>
 * that allowing configuring a {@link AggregationStrategy} will invoke the {@link org.apache.camel.Service#start()}
 * and {@link org.apache.camel.Service#stop()} to control the lifecycle aligned with the EIP itself.
 * <p/>
 * If an implementation also implements {@link org.apache.camel.CamelContextAware} then any <a href="http://camel.apache.org/eip">EIP</a>
 * that allowing configuring a {@link AggregationStrategy} will inject the {@link org.apache.camel.CamelContext} prior
 * to using the aggregation strategy.
 *
 * @version 
 */
public interface AggregationStrategy {

    // TODO: In Camel 3.0 we should move this to org.apache.camel package

    /**
     * Aggregates an old and new exchange together to create a single combined exchange
     *
     * @param oldExchange the oldest exchange (is <tt>null</tt> on first aggregation as we only have the new exchange)
     * @param newExchange the newest exchange (can be <tt>null</tt> if there was no data possible to acquire)
     * @return a combined composite of the two exchanges, favor returning the <tt>oldExchange</tt> whenever possible
     */
    Exchange aggregate(Exchange oldExchange, Exchange newExchange);
}
