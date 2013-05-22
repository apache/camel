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

package org.apache.camel.component.disruptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.util.UnitOfWorkHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a mutable reference to an {@link Exchange}, used as contents of the Disruptors ringbuffer
 */
public class ExchangeEvent {

    private static final Logger LOG = LoggerFactory.getLogger(ExchangeEvent.class);

    private Exchange exchange;

    private volatile int expectedConsumers;

    private final AtomicInteger processedConsumers = new AtomicInteger(0);

    private final AtomicReference<List<Exchange>> results = new AtomicReference<List<Exchange>>(new ArrayList <Exchange>());

    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(final Exchange exchange, int expectedConsumers) {
        this.exchange = exchange;
        this.expectedConsumers = expectedConsumers;
        processedConsumers.set(0);
    }

    public void consumed(Exchange result) {
        if (expectedConsumers > 1) {
            results.get().add(result);
        }

        if (processedConsumers.incrementAndGet() == expectedConsumers) {
            // all consumers are done processing
            if (expectedConsumers == 1) {
                // this was the only consumer, call synchronizations with this result
                UnitOfWorkHelper.doneSynchronizations(result, exchange.handoverCompletions(), LOG);
            } else {
                // this was the last consumer but we had more
                // set the list of results as GROUPED_EXCHANGE property on the original exchange instead
                List<Exchange> localResults = results.getAndSet(new ArrayList<Exchange>());
                exchange.setProperty(Exchange.GROUPED_EXCHANGE, localResults);
                UnitOfWorkHelper.doneSynchronizations(exchange, exchange.handoverCompletions(), LOG);
            }
        }
    }

    @Override
    public String toString() {
        return "ExchangeEvent{" + "exchange=" + exchange + '}';
    }
}
