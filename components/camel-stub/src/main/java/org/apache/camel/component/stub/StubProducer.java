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
package org.apache.camel.component.stub;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.component.seda.QueueReference;
import org.apache.camel.component.seda.SedaConsumerNotAvailableException;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.component.seda.SedaProducer;
import org.apache.camel.support.UnitOfWorkHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StubProducer extends SedaProducer {

    private static final Logger LOG = LoggerFactory.getLogger(StubProducer.class);

    private final Lock fallbackLock = new ReentrantLock();

    public StubProducer(SedaEndpoint endpoint, WaitForTaskToComplete waitForTaskToComplete, long timeout,
                        boolean blockWhenFull, boolean discardWhenFull, long offerTimeout) {
        super(endpoint, waitForTaskToComplete, timeout, blockWhenFull, discardWhenFull, offerTimeout);
    }

    @Override
    public StubEndpoint getEndpoint() {
        return (StubEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        AsyncCallback cb = callback;

        QueueReference queueReference = getEndpoint().getQueueReference();
        boolean empty = queueReference == null || !queueReference.hasConsumers();

        // if no consumers then use InOnly mode
        final ExchangePattern pattern = exchange.getPattern();
        if (empty && pattern != ExchangePattern.InOnly) {
            exchange.setPattern(ExchangePattern.InOnly);
            cb = doneSync -> {
                // and restore the old pattern after processing
                exchange.setPattern(pattern);
                callback.done(doneSync);
            };
        }

        return super.process(exchange, cb);
    }

    /**
     * Without a consumer the queue is never drained, so the stub keeps the latest messages (up to the size of the
     * queue) for browsing, and drops the oldest to make room. With a consumer the message is queued as seda does.
     */
    @Override
    protected void addToQueue(Exchange exchange, boolean copy) throws SedaConsumerNotAvailableException, IOException {
        StubEndpoint endpoint = getEndpoint();
        QueueReference queueReference = endpoint.getQueueReference();
        if (queueReference == null || queueReference.hasConsumers()
                || endpoint.isFailIfNoConsumers() || endpoint.isDiscardIfNoConsumers()) {
            super.addToQueue(exchange, copy);
            return;
        }

        BlockingQueue<Exchange> queue = queueReference.getQueue();
        List<Exchange> dropped = new ArrayList<>();
        // the endpoints of the queue share the lock of the component, so only one producer makes room at a time
        Lock lock = endpoint.getComponent() instanceof StubComponent sc ? sc.getNoConsumerLock() : fallbackLock;
        lock.lock();
        try {
            while (queue != null && queue.remainingCapacity() == 0) {
                Exchange oldest = queue.poll();
                if (oldest == null) {
                    break;
                }
                dropped.add(oldest);
            }
            super.addToQueue(exchange, copy);
        } finally {
            lock.unlock();
        }

        for (Exchange oldest : dropped) {
            LOG.trace("Dropping the oldest Exchange as the queue is full and has no consumers: {}", oldest);
            // the dropped message is done (such as a consumer committing the message it came from)
            UnitOfWorkHelper.doneSynchronizations(oldest, oldest.getExchangeExtension().handoverCompletions());
        }
    }

}
