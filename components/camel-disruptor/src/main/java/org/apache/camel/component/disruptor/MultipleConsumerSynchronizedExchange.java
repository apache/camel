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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.util.ExchangeHelper;

/**
 * Implementation of the {@link SynchronizedExchange} interface that correctly handles all completion
 * synchronisation courtesies for multiple consumers.
 */
public class MultipleConsumerSynchronizedExchange extends AbstractSynchronizedExchange {

    private final int expectedConsumers;

    private final AtomicInteger processedConsumers = new AtomicInteger(0);

    private final AtomicBoolean resultHandled = new AtomicBoolean(false);

    public MultipleConsumerSynchronizedExchange(Exchange exchange, int expectedConsumers) {
        super(exchange);
        this.expectedConsumers = expectedConsumers;
        processedConsumers.set(0);
    }


    public void consumed(Exchange result) {

        if (processedConsumers.incrementAndGet() == expectedConsumers || result.getException() != null 
            && !resultHandled.getAndSet(true)) {
            // all consumers are done processing or an exception occurred

            //SEDA Does not configure an aggregator in the internally used MulticastProcessor
            //As a result, the behaviour of SEDA in case of multicast is to only copy the results on an error
            if (result.getException() != null) {
                ExchangeHelper.copyResults(getExchange(), result);
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
