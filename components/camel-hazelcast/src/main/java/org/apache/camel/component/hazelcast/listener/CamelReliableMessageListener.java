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
package org.apache.camel.component.hazelcast.listener;

import com.hazelcast.topic.ReliableMessageListener;
import org.apache.camel.component.hazelcast.HazelcastDefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The listener of a reliable topic consumer. A reliable topic keeps its messages in a ringbuffer: a listener that falls
 * behind by more than its capacity (or its time to live) loses the messages that were overwritten. Hazelcast removes a
 * listener that is not loss tolerant at that point, for good, which would leave the consumer started without receiving
 * anything again. This listener reports the loss and goes on with the oldest message still in the ringbuffer.
 */
public class CamelReliableMessageListener extends CamelMessageListener implements ReliableMessageListener<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(CamelReliableMessageListener.class);

    private final String cacheName;
    // the sequence of the last message received, -1 before the first one
    private long lastSequence = -1;

    public CamelReliableMessageListener(HazelcastDefaultConsumer consumer, String cacheName) {
        super(consumer, cacheName);
        this.cacheName = cacheName;
    }

    @Override
    public long retrieveInitialSequence() {
        // the messages published from now on, as with a plain message listener
        return -1;
    }

    @Override
    public void storeSequence(long sequence) {
        // called by one thread at a time, for each message in order
        if (lastSequence >= 0 && sequence > lastSequence + 1) {
            LOG.warn("The consumer of the reliable topic {} lost {} messages: they were overwritten in the ringbuffer"
                     + " before they were received",
                    cacheName, sequence - lastSequence - 1);
        }
        lastSequence = sequence;
    }

    @Override
    public boolean isLossTolerant() {
        return true;
    }

    @Override
    public boolean isTerminal(Throwable failure) {
        // the route's failures are handled by the consumer's exception handler and do not reach Hazelcast
        return false;
    }
}
