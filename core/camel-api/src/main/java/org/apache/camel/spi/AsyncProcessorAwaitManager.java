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
package org.apache.camel.spi;

import java.util.Collection;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.StaticService;

/**
 * A manager to handle async routing engine, when {@link Exchange}s are being handed over from one thread to another, while
 * the callee thread is blocked waiting for the other threads to complete, before it can continue.
 * <p/>
 * This manager offers insight into the state, and allow to force stuck exchanges to be continued and for blocked threads
 * to be unblocked, which may happen in case of severe malfunctions (such as the system runs out of memory, a 3rd party
 * never responding, or a timeout not triggering, etc).
 */
public interface AsyncProcessorAwaitManager extends StaticService {

    /**
     * Utilization statistics of the this manager.
     */
    interface Statistics {

        /**
         * Total number of threads that has been blocked
         */
        long getThreadsBlocked();

        /**
         * Total number of threads that has been forced interrupted
         */
        long getThreadsInterrupted();

        /**
         * The total duration time in millis.
         */
        long getTotalDuration();

        /**
         * The lowest duration time in millis.
         */
        long getMinDuration();

        /**
         * The highest duration time in millis.
         */
        long getMaxDuration();

        /**
         * The average duration time in millis.
         */
        long getMeanDuration();

        /**
         * Reset the counters
         */
        void reset();

        /**
         * Whether statistics is enabled.
         */
        boolean isStatisticsEnabled();

        /**
         * Sets whether statistics is enabled.
         *
         * @param statisticsEnabled <tt>true</tt> to enable
         */
        void setStatisticsEnabled(boolean statisticsEnabled);
    }

    /**
     * Information about the thread and exchange that are inflight.
     */
    interface AwaitThread {

        /**
         * The thread which is blocked waiting for other threads to signal the callback.
         */
        Thread getBlockedThread();

        /**
         * The exchange being processed by the other thread.
         */
        Exchange getExchange();

        /**
         * Time in millis the thread has been blocked waiting for the signal.
         */
        long getWaitDuration();

        /**
         * The id of the route where the exchange was processed when the thread was set to block.
         * <p/>
         * Is <tt>null</tt> if message history is disabled.
         */
        String getRouteId();

        /**
         * The id of the node from the route where the exchange was processed when the thread was set to block.
         * <p/>
         * Is <tt>null</tt> if message history is disabled.
         */
        String getNodeId();

    }

    /**
     * Process the given exchange sychronously.
     *
     * @param processor the async processor to call
     * @param exchange the exchange to process
     */
    void process(AsyncProcessor processor, Exchange exchange);

    /**
     * Number of threads that are blocked waiting for other threads to trigger the callback when they are done processing
     * the exchange.
     */
    int size();

    /**
     * A <i>read-only</i> browser of the {@link AwaitThread}s that are currently inflight.
     */
    Collection<AwaitThread> browse();

    /**
     * To interrupt an exchange which may seem as stuck, to force the exchange to continue,
     * allowing any blocking thread to be released.
     * <p/>
     * <b>Important:</b> Use this with caution as the other thread is still assumed to be process the exchange. Though
     * if it appears as the exchange is <i>stuck</i>, then this method can remedy this, by forcing the latch to count-down
     * so the blocked thread can continue. An exception is set on the exchange which allows Camel's error handler to deal
     * with this malfunctioned exchange.
     *
     * @param exchangeId    the exchange id to interrupt.
     */
    void interrupt(String exchangeId);

    /**
     * To interrupt an exchange which may seem as stuck, to force the exchange to continue,
     * allowing any blocking thread to be released.
     * <p/>
     * <b>Important:</b> Use this with caution as the other thread is still assumed to be process the exchange. Though
     * if it appears as the exchange is <i>stuck</i>, then this method can remedy this, by forcing the latch to count-down
     * so the blocked thread can continue. An exception is set on the exchange which allows Camel's error handler to deal
     * with this malfunctioned exchange.
     *
     * @param exchange    the exchange to interrupt.
     */
    void interrupt(Exchange exchange);

    /**
     * Whether to interrupt any blocking threads during stopping.
     * <p/>
     * This is enabled by default which allows Camel to release any blocked thread during shutting down Camel itself.
     */
    boolean isInterruptThreadsWhileStopping();

    /**
     * Sets whether to interrupt any blocking threads during stopping.
     * <p/>
     * This is enabled by default which allows Camel to release any blocked thread during shutting down Camel itself.
     */
    void setInterruptThreadsWhileStopping(boolean interruptThreadsWhileStopping);

    /**
     * Gets the utilization statistics of this manager
     *
     * @return the utilization statistics
     */
    Statistics getStatistics();

}
