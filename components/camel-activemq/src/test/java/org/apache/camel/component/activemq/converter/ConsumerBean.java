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
package org.apache.camel.component.activemq.converter;

import java.util.ArrayList;
import java.util.List;

import jakarta.jms.Message;
import jakarta.jms.MessageListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConsumerBean implements MessageListener {
    private static final Logger LOG = LoggerFactory.getLogger(ConsumerBean.class);
    private final List<Message> messages = new ArrayList();
    private boolean verbose;
    private String id;

    public ConsumerBean() {
    }

    public List<Message> flushMessages() {
        List<Message> answer = null;
        synchronized (this.messages) {
            answer = new ArrayList(this.messages);
            this.messages.clear();
            return answer;
        }
    }

    public void onMessage(Message message) {
        synchronized (this.messages) {
            this.messages.add(message);
            if (this.verbose) {
                LOG.info("{} Received: {}", this.id, message);
            }

            this.messages.notifyAll();
        }
    }

    public void waitForMessagesToArrive(int messageCount, long maxWaitTime) {
        long maxRemainingMessageCount = getRemaining(messageCount);
        LOG.info("Waiting for ({}) message(s) to arrive", maxRemainingMessageCount);
        long start = System.currentTimeMillis();

        for (long endTime = start + maxWaitTime;
             maxRemainingMessageCount > 0L;
             maxRemainingMessageCount = getRemaining(
                     messageCount)) {
            try {
                synchronized (this.messages) {
                    this.messages.wait(1000L);
                }

                if (this.hasReceivedMessages(messageCount) || System.currentTimeMillis() > endTime) {
                    break;
                }
            } catch (InterruptedException var13) {
                LOG.info("Caught: {}", var13);
            }
        }

        long end = System.currentTimeMillis() - start;
        LOG.info("End of wait for {} millis", end);
    }

    private long getRemaining(int messageCount) {
        return Math.max(0, messageCount - this.messages.size());
    }

    public void assertMessagesArrived(int total, long maxWaitTime) {
        this.waitForMessagesToArrive(total, maxWaitTime);
        synchronized (this.messages) {
            int count = this.messages.size();
            assertEquals((long) total, (long) count, "Messages received");
        }
    }

    public List<Message> getMessages() {
        return this.messages;
    }

    protected boolean hasReceivedMessages(int messageCount) {
        synchronized (this.messages) {
            return this.messages.size() >= messageCount;
        }
    }
}
