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
package org.apache.camel;

import org.slf4j.LoggerFactory;

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
 * to acquire. Such as when using a <tt>PollEnricher</tt> to poll from a JMS queue which
 * is empty and a timeout was set.
 * <p/>
 * Important: In the aggregate method, do not create a new exchange instance to return,
 * instead return either the old or new exchange from the input parameters; favor returning the old exchange whenever possible.
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
 */
public interface AggregationStrategy {

    /**
     * Aggregates an old and new exchange together to create a single combined exchange
     * <p/>
     * Important: In the aggregate method, do not create a new exchange instance to return,
     * instead return either the old or new exchange from the input parameters; favor returning the old exchange whenever possible.
     *
     * @param oldExchange the oldest exchange (is <tt>null</tt> on first aggregation as we only have the new exchange)
     * @param newExchange the newest exchange (can be <tt>null</tt> if there was no data possible to acquire)
     * @return a combined composite of the two exchanges, return either the old or new exchange from the input parameters;
     * favor returning the old exchange whenever possible)
     */
    Exchange aggregate(Exchange oldExchange, Exchange newExchange);

    /**
     * Aggregates an old and new exchange together to create a single combined exchange.
     * <p/>
     * Important: In the aggregate method, do not create a new exchange instance to return,
     * instead return either the old or new exchange from the input parameters; favor returning the old exchange whenever possible.
     * <p/>
     * Important: Only Multicast and Recipient List EIP supports this method with access to the input exchange. All other EIPs
     * does not and uses the {@link #aggregate(Exchange, Exchange)} method instead.
     *
     * @param oldExchange    the oldest exchange (is <tt>null</tt> on first aggregation as we only have the new exchange)
     * @param newExchange    the newest exchange (can be <tt>null</tt> if there was no data possible to acquire)
     * @param inputExchange  the input exchange (input to the EIP)
     * @return a combined composite of the two exchanges, return either the old or new exchange from the input parameters;
     * favor returning the old exchange whenever possible)
     */
    default Exchange aggregate(Exchange oldExchange, Exchange newExchange, Exchange inputExchange) {
        return aggregate(oldExchange, newExchange);
    }

    /**
     * Indicates if this aggregation strategy uses pre-completion mode.
     * @return <tt>true</tt> if this strategy uses pre-completion mode, or <tt>false</tt> otherwise.
     */
    default boolean canPreComplete() {
        return false;
    }

    /**
     * Determines if the aggregation should complete the current group, and start a new group, or the aggregation
     * should continue using the current group. This callback will only be called if {@link #canPreComplete()}
     * returns <tt>true</tt>.
     *
     * @param oldExchange the oldest exchange (is <tt>null</tt> on first aggregation as we only have the new exchange)
     * @param newExchange the newest exchange (can be <tt>null</tt> if there was no data possible to acquire)
     * @return <tt>true</tt> to complete current group and start a new group, or <tt>false</tt> to keep using current
     */
    default boolean preComplete(Exchange oldExchange, Exchange newExchange) {
        return false;
    }

    /**
     * The aggregated {@link Exchange} has completed
     *
     * <b>Important: </b> This method must <b>not</b> throw any exceptions.
     *
     * @param exchange  the current aggregated exchange, or the original {@link org.apache.camel.Exchange} if no aggregation
     *                  has been done before the completion occurred
     */
    default void onCompletion(Exchange exchange) {
    }

    /**
     * A timeout occurred.
     * <p/>
     * <b>Important: </b> This method must <b>not</b> throw any exceptions.
     *
     * @param exchange  the current aggregated exchange, or the original {@link Exchange} if no aggregation
     *                  has been done before the timeout occurred
     * @param index     the index, may be <tt>-1</tt> if not possible to determine the index
     * @param total     the total, may be <tt>-1</tt> if not possible to determine the total
     * @param timeout   the timeout value in millis, may be <tt>-1</tt> if not possible to determine the timeout
     */
    default void timeout(Exchange exchange, int index, int total, long timeout) {
        // log a WARN we timed out since it will not be aggregated and the Exchange will be lost
        LoggerFactory.getLogger(getClass()).warn("Parallel processing timed out after {} millis for number {}. This task will be cancelled and will not be aggregated.", timeout, index);
    }

    /**
     * Callback when the aggregated {@link Exchange} fails to add
     * in the {@link org.apache.camel.spi.OptimisticLockingAggregationRepository} because of
     * an {@link org.apache.camel.spi.OptimisticLockingAggregationRepository.OptimisticLockingException}.
     * <p/>
     * Please note that when aggregating {@link Exchange}'s to be careful not to modify and return the {@code oldExchange}
     * from the {@link AggregationStrategy#aggregate(org.apache.camel.Exchange, org.apache.camel.Exchange)} method.
     * If you are using the default MemoryAggregationRepository this will mean you have modified the value of an object
     * already referenced/stored by the MemoryAggregationRepository. This makes it impossible for optimistic locking
     * to work correctly with the MemoryAggregationRepository.
     * <p/>
     * You should instead return either the new {@code newExchange} or a completely new instance of {@link Exchange}. This
     * is due to the nature of how the underlying {@link java.util.concurrent.ConcurrentHashMap} performs CAS operations
     * on the value identity.
     *
     * @see java.util.concurrent.ConcurrentHashMap
     */
    default void onOptimisticLockFailure(Exchange oldExchange, Exchange newExchange) {
        LoggerFactory.getLogger(getClass()).trace("onOptimisticLockFailure with AggregationStrategy: {}, oldExchange: {}, newExchange: {}", this, oldExchange, newExchange);
    }

}
