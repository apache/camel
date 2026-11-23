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
package org.apache.camel.component.jms;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.jms.reply.QueueReplyManager;
import org.apache.camel.component.jms.reply.ReplyManager;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A request that passed the running check of the producer before the producer was stopped must not create a reply
 * manager after the stop: nothing would stop it (its listener, temporary queue and threads). The reply managers are not
 * started here, as these paths do not need a broker.
 */
class JmsProducerStopReplyManagerTest {

    private final AtomicInteger created = new AtomicInteger();
    private CamelContext context;
    private JmsProducer producer;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.addComponent("jms", JmsComponent.jmsComponent(new NoBrokerConnectionFactory()));
        context.start();
        JmsEndpoint endpoint = context.getEndpoint("jms:queue:test?requestTimeout=1000", JmsEndpoint.class);
        producer = new JmsProducer(endpoint) {
            @Override
            protected ReplyManager createReplyManager() {
                created.incrementAndGet();
                return new QueueReplyManager(context);
            }

            @Override
            protected ReplyManager createReplyManager(String replyTo) {
                created.incrementAndGet();
                return new QueueReplyManager(context);
            }
        };
        producer.start();
    }

    @AfterEach
    void tearDown() {
        producer.stop();
        context.stop();
    }

    @Test
    void testNoReplyManagerAfterStop() throws Exception {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        // the request waits after the running check of the producer, where it reads the destination name header
        BlockingMessage in = new BlockingMessage(context, JmsConstants.JMS_DESTINATION_NAME);
        in.setBody("Hello");
        exchange.setIn(in);
        CountDownLatch done = new CountDownLatch(1);
        Thread sender = new Thread(() -> producer.process(exchange, doneSync -> done.countDown()));
        sender.start();
        assertThat(in.arrived.await(10, TimeUnit.SECONDS)).isTrue();

        producer.stop();
        in.release.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        sender.join(10000);

        assertThat(created.get()).as("reply managers created after the producer stopped").isZero();
        assertThat(exchange.getException()).isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void testReplyManagerCreatedOnceWhileRunningAndAfterRestart() throws Exception {
        // no broker: the request fails when it is sent, after the reply manager was created
        assertThat(send().getException()).isNotNull();
        assertThat(send().getException()).isNotNull();
        assertThat(created.get()).isEqualTo(1);

        producer.stop();
        producer.start();
        assertThat(send().getException()).isNotNull();
        assertThat(created.get()).isEqualTo(2);
    }

    private Exchange send() throws InterruptedException {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getIn().setBody("Hello");
        CountDownLatch done = new CountDownLatch(1);
        producer.process(exchange, doneSync -> done.countDown());
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        return exchange;
    }

    private static final class BlockingMessage extends DefaultMessage {
        private final String blockOn;
        private final AtomicBoolean armed = new AtomicBoolean(true);
        private final CountDownLatch arrived = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        BlockingMessage(CamelContext context, String blockOn) {
            super(context);
            this.blockOn = blockOn;
        }

        @Override
        public <T> T getHeader(String name, Class<T> type) {
            if (blockOn.equals(name) && armed.compareAndSet(true, false)) {
                arrived.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.getHeader(name, type);
        }
    }

    /**
     * A connection factory without a broker: every connection fails.
     */
    private static final class NoBrokerConnectionFactory implements ConnectionFactory {
        @Override
        public Connection createConnection() throws JMSException {
            throw new JMSException("No broker");
        }

        @Override
        public Connection createConnection(String userName, String password) throws JMSException {
            throw new JMSException("No broker");
        }

        @Override
        public JMSContext createContext() {
            throw new UnsupportedOperationException("No broker");
        }

        @Override
        public JMSContext createContext(String userName, String password) {
            throw new UnsupportedOperationException("No broker");
        }

        @Override
        public JMSContext createContext(String userName, String password, int sessionMode) {
            throw new UnsupportedOperationException("No broker");
        }

        @Override
        public JMSContext createContext(int sessionMode) {
            throw new UnsupportedOperationException("No broker");
        }
    }
}
