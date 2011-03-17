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

import java.util.Map;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.IQueue;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.impl.DefaultProducer;

/**
 *
 */
public class HazelcastQueueProducer extends DefaultProducer {

    private IQueue<Object> queue;
    private HazelcastComponentHelper helper = new HazelcastComponentHelper();

    public HazelcastQueueProducer(Endpoint endpoint, String queueName) {
        super(endpoint);
        this.queue = Hazelcast.getQueue(queueName);
    }

    public void process(Exchange exchange) throws Exception {

        Map<String, Object> headers = exchange.getIn().getHeaders();

        // get header parameters
        int operation = -1;

        if (headers.containsKey(HazelcastConstants.OPERATION)) {
            if (headers.get(HazelcastConstants.OPERATION) instanceof String) {
                operation = helper.lookupOperationNumber((String) headers.get(HazelcastConstants.OPERATION));
            } else {
                operation = (Integer) headers.get(HazelcastConstants.OPERATION);
            }
        }

        switch (operation) {

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
}
