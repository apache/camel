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
package org.apache.camel.component.hazelcast.atomicnumber;

import java.util.Map;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IAtomicLong;

import org.apache.camel.Exchange;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultEndpoint;
import org.apache.camel.component.hazelcast.HazelcastDefaultProducer;
import org.apache.camel.component.hazelcast.HazelcastOperation;
import org.apache.camel.util.ObjectHelper;

public class HazelcastAtomicnumberProducer extends HazelcastDefaultProducer {

    private final IAtomicLong atomicnumber;

    public HazelcastAtomicnumberProducer(HazelcastInstance hazelcastInstance, HazelcastDefaultEndpoint endpoint, String cacheName) {
        super(endpoint);
        this.atomicnumber = hazelcastInstance.getAtomicLong(cacheName);
    }

    public void process(Exchange exchange) throws Exception {

        Map<String, Object> headers = exchange.getIn().getHeaders();
        
        long expectedValue = 0L;
        
        if (headers.containsKey(HazelcastConstants.EXPECTED_VALUE)) {
            expectedValue = (long) headers.get(HazelcastConstants.EXPECTED_VALUE);
        }
        
        HazelcastOperation operation = lookupOperation(exchange);

        switch (operation) {

        case INCREMENT:
            this.increment(exchange);
            break;

        case DECREMENT:
            this.decrement(exchange);
            break;
            
        case COMPARE_AND_SET:
            this.compare(expectedValue, exchange);
            break;
            
        case GET_AND_ADD:
            this.getAndAdd(exchange);
            break;

        case SET_VALUE:
            this.set(exchange);
            break;

        case GET:
            this.get(exchange);
            break;

        case DESTROY:
            this.destroy();
            break;

        default:
            throw new IllegalArgumentException(String.format("The value '%s' is not allowed for parameter '%s' on the ATOMICNUMBER.", operation, HazelcastConstants.OPERATION));
        }

        // finally copy headers
        HazelcastComponentHelper.copyHeaders(exchange);
    }

    private void get(Exchange exchange) {
        exchange.getOut().setBody(this.atomicnumber.get());
    }

    private void increment(Exchange exchange) {
        exchange.getOut().setBody(this.atomicnumber.incrementAndGet());
    }

    private void decrement(Exchange exchange) {
        exchange.getOut().setBody(this.atomicnumber.decrementAndGet());
    }
    
    private void compare(long expected, Exchange exchange) {
        long update = exchange.getIn().getBody(Long.class);
        if (ObjectHelper.isEmpty(expected)) {
            throw new IllegalArgumentException("Expected value must be specified");
        }
        exchange.getOut().setBody(this.atomicnumber.compareAndSet(expected, update));
    }
    
    private void getAndAdd(Exchange exchange) {
        long delta = exchange.getIn().getBody(Long.class);
        exchange.getOut().setBody(this.atomicnumber.getAndAdd(delta));
    }

    private void set(Exchange exchange) {
        this.atomicnumber.set(exchange.getIn().getBody(Long.class));
    }

    private void destroy() {
        this.atomicnumber.destroy();
    }

}
