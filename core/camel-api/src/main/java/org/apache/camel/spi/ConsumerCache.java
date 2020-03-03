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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Service;

/**
 * Cache containing created {@link PollingConsumer}.
 */
public interface ConsumerCache extends Service {

    /**
     * Acquires a pooled polling consumer which you <b>must</b> release back again after usage using the
     * {@link #releasePollingConsumer(Endpoint, PollingConsumer)} method.
     *
     * @param endpoint the endpoint
     * @return the consumer
     */
    PollingConsumer acquirePollingConsumer(Endpoint endpoint);

    /**
     * Releases an acquired polling consumer back after usage.
     *
     * @param endpoint the endpoint
     * @param pollingConsumer the consumer to release
     */
    void releasePollingConsumer(Endpoint endpoint, PollingConsumer pollingConsumer);

    /**
     * Waits until a message is available and then returns it. Warning that this
     * method could block indefinitely if no messages are available.
     * <p/>
     * Will return <tt>null</tt> if the consumer is not started
     * <p/>
     * <b>Important: </b> See the class javadoc about the need for done the {@link org.apache.camel.spi.UnitOfWork}
     * on the returned {@link Exchange}
     *
     * @param endpoint  the endpoint to receive from
     * @return the message exchange received.
     */
    Exchange receive(Endpoint endpoint);

    /**
     * Attempts to receive a message exchange, waiting up to the given timeout
     * to expire if a message is not yet available.
     * <p/>
     * <b>Important: </b> See the class javadoc about the need for done the {@link org.apache.camel.spi.UnitOfWork}
     * on the returned {@link Exchange}
     *
     * @param endpoint  the endpoint to receive from
     * @param timeout the amount of time in milliseconds to wait for a message
     *                before timing out and returning <tt>null</tt>
     *
     * @return the message exchange if one was available within the timeout
     *         period, or <tt>null</tt> if the timeout expired
     */
    Exchange receive(Endpoint endpoint, long timeout);

    /**
     * Attempts to receive a message exchange immediately without waiting and
     * returning <tt>null</tt> if a message exchange is not available yet.
     * <p/>
     * <b>Important: </b> See the class javadoc about the need for done the {@link org.apache.camel.spi.UnitOfWork}
     * on the returned {@link Exchange}
     *
     * @param endpoint  the endpoint to receive from
     *
     * @return the message exchange if one is immediately available otherwise
     *         <tt>null</tt>
     */
    Exchange receiveNoWait(Endpoint endpoint);

    /**
     * Gets the source which uses this cache
     *
     * @return the source
     */
    Object getSource();

    /**
     * Gets the maximum cache size (capacity).
     *
     * @return the capacity
     */
    int getCapacity();

    /**
     * Returns the current size of the cache
     *
     * @return the current size
     */
    int size();

    /**
     * Purges this cache
     */
    void purge();

    /**
     * Cleanup the cache (purging stale entries)
     */
    void cleanUp();

    /**
     * Gets the endpoint statistics
     */
    EndpointUtilizationStatistics getEndpointUtilizationStatistics();
}
