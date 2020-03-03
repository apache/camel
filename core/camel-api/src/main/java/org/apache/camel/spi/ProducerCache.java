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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Service;

/**
 * Cache containing created {@link Producer}.
 */
public interface ProducerCache extends Service {

    /**
     * Acquires a pooled producer which you <b>must</b> release back again after usage using the
     * {@link #releaseProducer(org.apache.camel.Endpoint, org.apache.camel.AsyncProducer)} method.
     * <p/>
     * If the producer is currently starting then the cache will wait at most 30 seconds for the producer
     * to finish starting and be ready for use.
     *
     * @param endpoint the endpoint
     * @return the producer
     */
    AsyncProducer acquireProducer(Endpoint endpoint);

    /**
     * Releases an acquired producer back after usage.
     *
     * @param endpoint the endpoint
     * @param producer the producer to release
     */
    void releaseProducer(Endpoint endpoint, AsyncProducer producer);

    /**
     * Sends the exchange to the given endpoint.
     * <p>
     * This method will <b>not</b> throw an exception. If processing of the given
     * Exchange failed then the exception is stored on the provided Exchange
     *
     * @param endpoint the endpoint to send the exchange to
     * @param exchange the exchange to send
     * @throws RejectedExecutionException is thrown if CamelContext is stopped
     */
    Exchange send(Endpoint endpoint, Exchange exchange, Processor resultProcessor);

    /**
     * Asynchronously sends an exchange to an endpoint using a supplied
     * {@link Processor} to populate the exchange
     * <p>
     * This method will <b>neither</b> throw an exception <b>nor</b> complete future exceptionally.
     * If processing of the given Exchange failed then the exception is stored on the return Exchange
     *
     * @param endpoint        the endpoint to send the exchange to
     * @param pattern         the message {@link ExchangePattern} such as
     *                        {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param processor       the transformer used to populate the new exchange
     * @param resultProcessor a processor to process the exchange when the send is complete.
     * @param exchange        an exchange to use in processing. Exchange will be created if parameter is null.
     * @param future          the preexisting future to complete when processing is done or null if to create new one
     * @return future that completes with exchange when processing is done. Either passed into future parameter
     *              or new one if parameter was null
     */
    CompletableFuture<Exchange> asyncSendExchange(Endpoint endpoint, ExchangePattern pattern,
            Processor processor, Processor resultProcessor, Exchange exchange, CompletableFuture<Exchange> future);

    /**
     * Gets the source which uses this cache
     *
     * @return the source
     */
    Object getSource();

    /**
     * Returns the current size of the cache
     *
     * @return the current size
     */
    int size();

    /**
     * Gets the maximum cache size (capacity).
     *
     * @return the capacity
     */
    int getCapacity();

    /**
     * Purges this cache
     */
    void purge();

    /**
     * Cleanup the cache (purging stale entries)
     */
    void cleanUp();

    /**
     * Whether {@link org.apache.camel.spi.EventNotifier} is enabled
     */
    boolean isEventNotifierEnabled();

    /**
     * Sets whether {@link org.apache.camel.spi.EventNotifier} is enabled
     */
    void setEventNotifierEnabled(boolean eventNotifierEnabled);

    /**
     * Gets the endpoint statistics
     */
    EndpointUtilizationStatistics getEndpointUtilizationStatistics();

    /**
     * Sends an exchange to an endpoint using a supplied callback supporting the asynchronous routing engine.
     * <p/>
     * If an exception was thrown during processing, it would be set on the given Exchange
     *
     * @param endpoint         the endpoint to send the exchange to
     * @param exchange         the exchange, can be <tt>null</tt> if so then create a new exchange from the producer
     * @param callback         the asynchronous callback
     * @param producerCallback the producer template callback to be executed
     * @return (doneSync) <tt>true</tt> to continue execute synchronously, <tt>false</tt> to continue being executed asynchronously
     */
    boolean doInAsyncProducer(Endpoint endpoint, Exchange exchange, AsyncCallback callback, AsyncProducerCallback producerCallback);

    /**
     * Callback for sending a exchange message to a endpoint using an {@link AsyncProcessor} capable producer.
     * <p/>
     * Using this callback as a template pattern ensures that Camel handles the resource handling and will
     * start and stop the given producer, to avoid resource leaks.
     */
    interface AsyncProducerCallback {

        /**
         * Performs operation on the given producer to send the given exchange.
         *
         * @param asyncProducer   the async producer, is never <tt>null</tt>
         * @param exchange        the exchange to process
         * @param callback        the async callback
         * @return (doneSync) <tt>true</tt> to continue execute synchronously, <tt>false</tt> to continue being executed asynchronously
         */
        boolean doInAsyncProducer(AsyncProducer asyncProducer, Exchange exchange, AsyncCallback callback);
    }

}
