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
package org.apache.camel.component.disruptor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.support.ExchangeHelper;

/**
 * Implementation of the {@link SynchronizedExchange} interface that correctly handles all completion synchronisation
 * courtesies for multiple consumers.
 */
public class MultipleConsumerSynchronizedExchange extends AbstractSynchronizedExchange {

    private final int expectedConsumers;

    // the consumers copy this one, so that none of them sees (or reads while it is written) the result of a failure,
    // which is copied into the exchange when all consumers are done
    private final Exchange exchangeToCopy;

    private final AtomicInteger processedConsumers = new AtomicInteger();

    private final AtomicBoolean resultHandled = new AtomicBoolean();

    // the result of the first consumer that failed, which is copied back when all consumers are done
    private final AtomicReference<Exchange> failure = new AtomicReference<>();

    public MultipleConsumerSynchronizedExchange(Exchange exchange, int expectedConsumers) {
        super(exchange);
        this.expectedConsumers = expectedConsumers;
        processedConsumers.set(0);
        // taken when the exchange is published, before any consumer can fail
        this.exchangeToCopy = ExchangeHelper.copyExchangeWithProperties(exchange, exchange.getContext());
    }

    @Override
    public Exchange getExchangeToCopy() {
        return exchangeToCopy;
    }

    @Override
    public void consumed(Exchange result) {
        if (result.getException() != null) {
            // before this consumer is counted, so that the consumer that is counted last knows of the failure: this
            // write comes before our incrementAndGet in program order, and the last consumer's incrementAndGet on the
            // same AtomicInteger happens after ours, so its failure.get() sees it
            failure.compareAndSet(null, result);
        }
        int processed = processedConsumers.incrementAndGet();

        // complete the exchange once, when all consumers are done (as SEDA): completing it at the first failure would
        // run the on completions of the caller (such as a rollback of the file consumer, or the reply of an InOut
        // producer) while the other consumers are still routing it
        if (processed == expectedConsumers && !resultHandled.getAndSet(true)) {
            Exchange failed = failure.get();
            if (failed != null) {
                // only the result of a failure is copied back (as SEDA, whose multicast to its consumers has no
                // aggregation strategy)
                ExchangeHelper.copyResults(getExchange(), failed);
            }
            performSynchronization();
        }
    }

    @Override
    public Exchange cancelAndGetOriginalExchange() {
        resultHandled.set(true);

        return super.cancelAndGetOriginalExchange();
    }
}
