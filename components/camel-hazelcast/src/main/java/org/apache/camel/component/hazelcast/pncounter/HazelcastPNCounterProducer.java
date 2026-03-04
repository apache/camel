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
package org.apache.camel.component.hazelcast.pncounter;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.crdt.pncounter.PNCounter;
import org.apache.camel.Exchange;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultEndpoint;
import org.apache.camel.component.hazelcast.HazelcastDefaultProducer;
import org.apache.camel.component.hazelcast.HazelcastOperation;

public class HazelcastPNCounterProducer extends HazelcastDefaultProducer {

    private final PNCounter pnCounter;

    public HazelcastPNCounterProducer(HazelcastInstance hazelcastInstance, HazelcastDefaultEndpoint endpoint,
                                      String cacheName) {
        super(endpoint);
        this.pnCounter = hazelcastInstance.getPNCounter(cacheName);
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        HazelcastOperation operation = lookupOperation(exchange);

        switch (operation) {

            case INCREMENT:
                this.increment(exchange);
                break;

            case DECREMENT:
                this.decrement(exchange);
                break;

            case GET_AND_ADD:
                this.getAndAdd(exchange);
                break;

            case GET:
                this.get(exchange);
                break;

            case DESTROY:
                this.destroy(exchange);
                break;

            default:
                throw new IllegalArgumentException(
                        String.format("The value '%s' is not allowed for parameter '%s' on the PNCOUNTER.", operation,
                                HazelcastConstants.OPERATION));
        }

        // finally copy headers
        HazelcastComponentHelper.copyHeaders(exchange);
    }

    private void get(Exchange exchange) {
        exchange.getMessage().setBody(this.pnCounter.get());
    }

    private void increment(Exchange exchange) {
        exchange.getMessage().setBody(this.pnCounter.incrementAndGet());
    }

    private void decrement(Exchange exchange) {
        exchange.getMessage().setBody(this.pnCounter.decrementAndGet());
    }

    private void getAndAdd(Exchange exchange) {
        long delta = exchange.getIn().getBody(Long.class);
        exchange.getMessage().setBody(this.pnCounter.getAndAdd(delta));
    }

    private void destroy(Exchange exchange) {
        this.pnCounter.destroy();
    }

}
