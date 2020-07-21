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
package org.apache.camel.component.hazelcast.queue;

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.Exchange;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultEndpoint;
import org.apache.camel.component.hazelcast.HazelcastDefaultProducer;
import org.apache.camel.component.hazelcast.HazelcastOperation;

/**
 *
 */
public class HazelcastQueueProducer extends HazelcastDefaultProducer {

    private IQueue<Object> queue;

    public HazelcastQueueProducer(HazelcastInstance hazelcastInstance, HazelcastDefaultEndpoint endpoint, String queueName) {
        super(endpoint);
        this.queue = hazelcastInstance.getQueue(queueName);
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        Map<String, Object> headers = exchange.getIn().getHeaders();

        // get header parameters
        Object drainToCollection = null;

        if (headers.containsKey(HazelcastConstants.DRAIN_TO_COLLECTION)) {
            drainToCollection = headers.get(HazelcastConstants.DRAIN_TO_COLLECTION);
        }

        final HazelcastOperation operation = lookupOperation(exchange);

        switch (operation) {

            case ADD:
                this.add(exchange);
                break;

            case PUT:
                this.put(exchange);
                break;

            case POLL:
                this.poll(exchange);
                break;

            case PEEK:
                this.peek(exchange);
                break;

            case OFFER:
                this.offer(exchange);
                break;

            case REMOVE_VALUE:
                this.remove(exchange);
                break;

            case REMAINING_CAPACITY:
                this.remainingCapacity(exchange);
                break;

            case REMOVE_ALL:
                this.removeAll(exchange);
                break;

            case REMOVE_IF:
                this.removeIf(exchange);
                break;

            case DRAIN_TO:
                this.drainTo((Collection) drainToCollection, exchange);
                break;

            case TAKE:
                this.take(exchange);
                break;

            case RETAIN_ALL:
                this.retainAll(exchange);
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

    private void removeAll(Exchange exchange) {
        Collection body = exchange.getIn().getBody(Collection.class);
        this.queue.removeAll(body);
    }

    private void removeIf(Exchange exchange) {
        Predicate filter = exchange.getIn().getBody(Predicate.class);
        exchange.getOut().setBody(this.queue.removeIf(filter));
    }

    private void take(Exchange exchange) throws InterruptedException {
        exchange.getOut().setBody(this.queue.take());
    }

    private void retainAll(Exchange exchange) {
        Collection body = exchange.getIn().getBody(Collection.class);
        this.queue.retainAll(body);
    }
}
