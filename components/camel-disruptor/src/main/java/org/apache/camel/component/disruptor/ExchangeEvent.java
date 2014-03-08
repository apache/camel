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

import org.apache.camel.Exchange;

/**
 * This is a mutable reference to an {@link Exchange}, used as contents of the Disruptors ringbuffer
 */
public class ExchangeEvent {

    private SynchronizedExchange synchronizedExchange;
    
    public SynchronizedExchange getSynchronizedExchange() {
        return synchronizedExchange;
    }

    public void setExchange(final Exchange exchange, int expectedConsumers) {
        synchronizedExchange = createSynchronizedExchange(exchange, expectedConsumers);
    }

    private SynchronizedExchange createSynchronizedExchange(Exchange exchange, int expectedConsumers) {
        if (expectedConsumers > 1) {
            return new MultipleConsumerSynchronizedExchange(exchange, expectedConsumers);
        } else {
            return new SingleConsumerSynchronizedExchange(exchange);
        }
    }

    @Override
    public String toString() {
        return "ExchangeEvent{" + "exchange=" + synchronizedExchange.getExchange()  + '}';
    }
}
