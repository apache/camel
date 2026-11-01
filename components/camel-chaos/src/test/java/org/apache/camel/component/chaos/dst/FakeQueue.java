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
package org.apache.camel.component.chaos.dst;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.test.dst.Dst;

/**
 * A queue that keeps a message until the route completes it, as a broker with client acknowledgement does, and its
 * consumer: a {@link ScheduledPollConsumer}, the base of the file, FTP and AWS SQS consumers, so it is suspended,
 * stopped and started as theirs are.
 * <p/>
 * The queue is the external system of the world; it lives outside the endpoint, so a route that is removed and added
 * back finds the same messages. A poll takes up to {@code maxMessagesPerPoll} visible messages and hands each to the
 * route without waiting for it (the asynchronous processor); while the route has a message, the message is not visible.
 * When the route is done with the exchange, a completed message is deleted and a failed one becomes visible again, to
 * be polled later. There are no latencies, faults or duplicate deliveries of its own: the faults of the world are the
 * chaos rules.
 */
final class FakeQueue {

    private final Dst dst;
    // the messages by id, in the order they were published: whether each is in flight
    private final Map<String, Boolean> messages = new LinkedHashMap<>();
    // the virtual times (dst.now()) of the deliveries to the route, for the invariants
    final List<Long> deliveries = new ArrayList<>();
    // the virtual time of the first delivery of each message
    final Map<String, Long> firstDelivery = new LinkedHashMap<>();
    int deleted;
    int failedBack;

    FakeQueue(Dst dst) {
        this.dst = dst;
    }

    void publish(String id) {
        messages.put(id, false);
    }

    boolean drained() {
        return messages.isEmpty();
    }

    int size() {
        return messages.size();
    }

    private List<String> receive(int max) {
        List<String> answer = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : messages.entrySet()) {
            if (answer.size() == max) {
                break;
            }
            if (!e.getValue()) {
                e.setValue(true);
                answer.add(e.getKey());
            }
        }
        return answer;
    }

    private void done(String id, boolean completed) {
        if (completed) {
            messages.remove(id);
            deleted++;
            dst.trace("queue: " + id + " deleted");
        } else {
            messages.put(id, false);
            failedBack++;
            dst.trace("queue: " + id + " failed, visible again");
        }
    }

    DefaultComponent component() {
        return new DefaultComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
                    throws Exception {
                QueueEndpoint endpoint = new QueueEndpoint(uri, this);
                setProperties(endpoint, parameters);
                return endpoint;
            }
        };
    }

    final class QueueEndpoint extends ScheduledPollEndpoint {

        private int maxMessagesPerPoll = 5;

        QueueEndpoint(String uri, DefaultComponent component) {
            super(uri, component);
        }

        public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
            this.maxMessagesPerPoll = maxMessagesPerPoll;
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException("The fake queue of the world has no producer");
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            QueueConsumer consumer = new QueueConsumer(this, processor, maxMessagesPerPoll);
            configureConsumer(consumer);
            return consumer;
        }
    }

    final class QueueConsumer extends ScheduledPollConsumer {

        private final int max;

        QueueConsumer(Endpoint endpoint, Processor processor, int max) {
            super(endpoint, processor);
            this.max = max;
        }

        @Override
        protected int poll() {
            List<String> batch = receive(max);
            for (String id : batch) {
                Exchange exchange = createExchange(false);
                exchange.getMessage().setBody(id);
                deliveries.add(dst.now());
                firstDelivery.putIfAbsent(id, dst.now());
                dst.trace("queue: " + id + " delivered");
                getAsyncProcessor().process(exchange, doneSync -> {
                    done(id, !exchange.isFailed() && !exchange.isRollbackOnly());
                    releaseExchange(exchange, false);
                });
            }
            return batch.size();
        }
    }
}
