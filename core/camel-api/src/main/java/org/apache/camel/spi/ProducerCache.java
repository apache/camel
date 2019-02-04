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
package org.apache.camel.spi;

import java.util.concurrent.CompletableFuture;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Service;

public interface ProducerCache extends Service {

    AsyncProducer acquireProducer(Endpoint endpoint);

    void releaseProducer(Endpoint endpoint, AsyncProducer producer);

    Exchange send(Endpoint endpoint, Exchange exchange, Processor resultProcessor);

    CompletableFuture<Exchange> asyncSendExchange(Endpoint endpoint, ExchangePattern pattern, 
            Processor processor, Processor resultProcessor, Exchange inExchange, CompletableFuture<Exchange> exchangeFuture);

    Object getSource();

    int size();

    int getCapacity();

    long getHits();

    long getMisses();

    long getEvicted();

    void resetCacheStatistics();

    void purge();

    void cleanUp();

    boolean isEventNotifierEnabled();

    void setEventNotifierEnabled(boolean eventNotifierEnabled);

    EndpointUtilizationStatistics getEndpointUtilizationStatistics();

    boolean doInAsyncProducer(Endpoint endpoint, Exchange exchange, AsyncCallback callback, AsyncProducerCallback asyncProducerCallback);

    /**
     * Callback for sending a exchange message to a endpoint using an {@link AsyncProcessor} capable producer.
     * <p/>
     * Using this callback as a template pattern ensures that Camel handles the resource handling and will
     * start and stop the given producer, to avoid resource leaks.
     *
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
