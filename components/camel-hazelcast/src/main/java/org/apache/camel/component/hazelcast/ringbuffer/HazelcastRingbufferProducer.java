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
package org.apache.camel.component.hazelcast.ringbuffer;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.ringbuffer.Ringbuffer;

import org.apache.camel.Exchange;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultEndpoint;
import org.apache.camel.component.hazelcast.HazelcastDefaultProducer;
import org.apache.camel.component.hazelcast.HazelcastOperation;

public class HazelcastRingbufferProducer extends HazelcastDefaultProducer {

    private final Ringbuffer<Object> ringbuffer;

    public HazelcastRingbufferProducer(HazelcastInstance hazelcastInstance, HazelcastDefaultEndpoint endpoint, String cacheName) {
        super(endpoint);
        this.ringbuffer = hazelcastInstance.getRingbuffer(cacheName);
    }

    public void process(Exchange exchange) throws Exception {

        HazelcastOperation operation = lookupOperation(exchange);

        switch (operation) {

        case READ_ONCE_HEAD:
            this.readOnceHead(exchange);
            break;
            
        case READ_ONCE_TAIL:
            this.readOnceTail(exchange);
            break;
            
        case CAPACITY:
            this.getCapacity(exchange);
            break;
            
        case REMAINING_CAPACITY:
            this.getRemainingCapacity(exchange);
            break;
            
        case ADD:
            this.add(exchange);
            break;

        default:
            throw new IllegalArgumentException(String.format("The value '%s' is not allowed for parameter '%s' on the RINGBUFFER.", operation, HazelcastConstants.OPERATION));
        }

        // finally copy headers
        HazelcastComponentHelper.copyHeaders(exchange);
    }

    private void readOnceHead(Exchange exchange) throws InterruptedException {
        exchange.getOut().setBody(this.ringbuffer.readOne(ringbuffer.headSequence()));
    }

    private void readOnceTail(Exchange exchange) throws InterruptedException {
        exchange.getOut().setBody(this.ringbuffer.readOne(ringbuffer.tailSequence()));
    }
    
    private void getCapacity(Exchange exchange) throws InterruptedException {
        exchange.getOut().setBody(this.ringbuffer.capacity());
    }
    
    private void getRemainingCapacity(Exchange exchange) throws InterruptedException {
        exchange.getOut().setBody(this.ringbuffer.remainingCapacity());
    }
    
    private void add(Exchange exchange) {
        final Object body = exchange.getIn().getBody();
        exchange.getOut().setBody(ringbuffer.add(body));
    }
}
