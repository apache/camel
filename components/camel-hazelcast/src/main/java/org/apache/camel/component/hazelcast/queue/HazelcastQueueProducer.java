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
package org.apache.camel.component.hazelcast.queue;

import java.util.Collection;
import java.util.Map;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IQueue;

import org.apache.camel.Exchange;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultEndpoint;
import org.apache.camel.component.hazelcast.HazelcastDefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 *
 */
public class HazelcastQueueProducer extends HazelcastDefaultProducer {

    private IQueue<Object> queue;

    public HazelcastQueueProducer(HazelcastInstance hazelcastInstance, HazelcastDefaultEndpoint endpoint, String queueName) {
        super(endpoint);
        this.queue = hazelcastInstance.getQueue(queueName);
    }

    public void process(Exchange exchange) throws Exception {
        
        Map<String, Object> headers = exchange.getIn().getHeaders();

        // get header parameters
        Object draintToCollection = null;
        
        if (headers.containsKey(HazelcastConstants.DRAIN_TO_COLLECTION)) {
            draintToCollection = headers.get(HazelcastConstants.DRAIN_TO_COLLECTION);
        }

        final int operation = lookupOperationNumber(exchange);

        switch (operation) {

        case -1:
            //If no operation is specified use ADD.
        case HazelcastConstants.ADD_OPERATION:
            this.add(exchange);
            break;

        case HazelcastConstants.PUT_OPERATION:
            this.put(exchange);
            break;

        case HazelcastConstants.POLL_OPERATION:
            this.poll(exchange);
            break;

        case HazelcastConstants.PEEK_OPERATION:
            this.peek(exchange);
            break;

        case HazelcastConstants.OFFER_OPERATION:
            this.offer(exchange);
            break;

        case HazelcastConstants.REMOVEVALUE_OPERATION:
            this.remove(exchange);
            break;

        case HazelcastConstants.REMAINING_CAPACITY_OPERATION:
            this.remainingCapacity(exchange);
            break;
            
        case HazelcastConstants.DRAIN_TO_OPERATION:
            if (ObjectHelper.isNotEmpty(draintToCollection)) {
                this.drainTo((Collection) draintToCollection, exchange);
            } else {
                throw new IllegalArgumentException("Drain to collection header must be specified");
            }
            break;
            
        default:
            throw new IllegalArgumentException(String.format("The value '%s' is not allowed for parameter '%s' on the QUEUE cache.", operation, HazelcastConstants.OPERATION));
        }

        // finally copy headers
        HazelcastComponentHelper.copyHeaders(exchange);

    }

    private void add(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        this.queue.add(body);
    }

    private void put(Exchange exchange) throws InterruptedException {
        Object body = exchange.getIn().getBody();
        this.queue.put(body);
    }

    private void poll(Exchange exchange) {
        exchange.getOut().setBody(this.queue.poll());
    }

    private void peek(Exchange exchange) {
        exchange.getOut().setBody(this.queue.peek());
    }

    private void offer(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        this.queue.offer(body);
    }

    private void remove(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        if (body != null) {
            this.queue.remove(body);
        } else {
            this.queue.remove();
        }
    }
    
    private void remainingCapacity(Exchange exchange) {
        exchange.getOut().setBody(this.queue.remainingCapacity());
    }
    
    private void drainTo(Collection c, Exchange exchange) {
        exchange.getOut().setBody(this.queue.drainTo(c));
        exchange.getOut().setHeader(HazelcastConstants.DRAIN_TO_COLLECTION, c);
    }
}