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

import org.jspecify.annotations.Nullable;

/**
 * A {@link PollingConsumer} used by the dynamic {@code poll} and {@code pollEnrich} EIPs, allowing the component to use
 * data from the current {@link Exchange} (such as headers or properties) to parameterise the poll operation at runtime.
 * <p/>
 * Components that support dynamic polling implement this interface in addition to {@link PollingConsumer} to provide
 * exchange-aware overloads of the three receive methods.
 *
 * @see   PollingConsumer
 * @see   Exchange
 * @since 4.11
 */
public interface DynamicPollingConsumer extends PollingConsumer {

    /**
     * Waits until a message is available and then returns it. Warning that this method could block indefinitely if no
     * messages are available.
     * <p/>
     * Will return <tt>null</tt> if the consumer is not started
     * <p/>
     * <b>Important: </b> See the class javadoc about the need for done the {@link org.apache.camel.spi.UnitOfWork} on
     * the returned {@link Exchange}
     *
     * @param  exchange the current exchange
     *
     * @return          the message exchange received.
     */
    @Nullable
    Exchange receive(Exchange exchange);

    /**
     * Attempts to receive a message exchange immediately without waiting and returning <tt>null</tt> if a message
     * exchange is not available yet.
     * <p/>
     * <b>Important: </b> See the class javadoc about the need for done the {@link org.apache.camel.spi.UnitOfWork} on
     * the returned {@link Exchange}
     *
     * @return the message exchange if one is immediately available otherwise <tt>null</tt>
     */
    @Nullable
    Exchange receiveNoWait(Exchange exchange);

    /**
     * Attempts to receive a message exchange, waiting up to the given timeout to expire if a message is not yet
     * available.
     * <p/>
     * <b>Important: </b> See the class javadoc about the need for done the {@link org.apache.camel.spi.UnitOfWork} on
     * the returned {@link Exchange}
     *
     * @param  timeout the amount of time in milliseconds to wait for a message before timing out and returning
     *                 <tt>null</tt>
     *
     * @return         the message exchange if one was available within the timeout period, or <tt>null</tt> if the
     *                 timeout expired
     */
    @Nullable
    Exchange receive(Exchange exchange, long timeout);
}
