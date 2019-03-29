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
package org.apache.camel.component.quickfixj;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import quickfix.Message;
import quickfix.SessionID;

public class MessageCorrelator implements QuickfixjEventListener {
    public static final long DEFAULT_CORRELATION_TIMEOUT = 1000L;
    private final List<MessageCorrelationRule> rules = new CopyOnWriteArrayList<>();

    public Callable<Message> getReply(SessionID sessionID, Exchange exchange)
        throws InterruptedException, ExchangeTimedOutException {

        MessagePredicate messageCriteria = (MessagePredicate) exchange.getProperty(QuickfixjProducer.CORRELATION_CRITERIA_KEY);
        final MessageCorrelationRule correlationRule = new MessageCorrelationRule(exchange, sessionID, messageCriteria);
        rules.add(correlationRule);

        final long timeout = exchange.getProperty(
            QuickfixjProducer.CORRELATION_TIMEOUT_KEY,
            DEFAULT_CORRELATION_TIMEOUT, Long.class);

        return new Callable<Message>() {
            @Override
            public Message call() throws Exception {
                if (!correlationRule.getLatch().await(timeout, TimeUnit.MILLISECONDS)) {
                    throw new ExchangeTimedOutException(correlationRule.getExchange(), timeout);
                }
                return correlationRule.getReplyMessage();
            }
        };
    }

    @Override
    public void onEvent(QuickfixjEventCategory eventCategory, SessionID sessionID, Message message) throws Exception {
        if (message != null) {
            for (MessageCorrelationRule rule : rules) {
                if (rule.getMessageCriteria().evaluate(message)) {
                    rule.setReplyMessage(message);
                    rules.remove(rule);
                    rule.getLatch().countDown();
                }
            }
        }
    }

    private static class MessageCorrelationRule {
        private final Exchange exchange;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final MessagePredicate messageCriteria;

        private Message replyMessage;

        MessageCorrelationRule(Exchange exchange, SessionID sessionID, MessagePredicate messageCriteria) {
            this.exchange = exchange;
            this.messageCriteria = messageCriteria;
        }

        public void setReplyMessage(Message message) {
            this.replyMessage = message;
        }

        public Message getReplyMessage() {
            return replyMessage;
        }

        public CountDownLatch getLatch() {
            return latch;
        }

        public Exchange getExchange() {
            return exchange;
        }

        public MessagePredicate getMessageCriteria() {
            return messageCriteria;
        }
    }
}
