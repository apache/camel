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
package org.apache.camel.component.rocketmq;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.TypeConversionException;
import org.apache.camel.component.rocketmq.reply.ReplyHolder;
import org.apache.camel.component.rocketmq.reply.ReplyManager;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.TypeConverterSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An InOut request that passed the running check of the producer just before the producer was stopped must not create a
 * reply manager (a consumer of the reply topic and a timeout checker thread) that nothing would stop.
 */
class RocketMQProducerStopReplyManagerTest extends CamelTestSupport {

    private static final String ROCKETMQ_URI = "rocketmq:STOP_TOPIC?namesrvAddr=localhost:9876&producerGroup=p1"
                                               + "&replyToTopic=REPLY_TOPIC&replyToConsumerGroup=g1";

    private final CountDownLatch converting = new CountDownLatch(1);
    private final CountDownLatch continueConverting = new CountDownLatch(1);
    // the reply managers that the producer created
    private final List<TestReplyManager> replyManagers = new CopyOnWriteArrayList<>();
    private RocketMQProducer producer;

    @AfterEach
    void stopProducer() {
        continueConverting.countDown();
        ServiceHelper.stopService(producer);
        replyManagers.forEach(ServiceHelper::stopService);
    }

    @Test
    void testNoReplyManagerAfterStop() throws Exception {
        // the body of the request is converted after the running check, and before the reply manager is created
        context.getTypeConverterRegistry().addTypeConverter(byte[].class, SlowBody.class, new TypeConverterSupport() {
            @Override
            public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
                converting.countDown();
                try {
                    continueConverting.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return type.cast(new byte[] { 1 });
            }
        });
        startProducer();

        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getIn().setBody(new SlowBody());
        CountDownLatch done = new CountDownLatch(1);
        Thread request = new Thread(() -> producer.process(exchange, doneSync -> done.countDown()), "request");
        request.start();
        assertTrue(converting.await(10, TimeUnit.SECONDS), "The request should convert its body");

        // the producer is stopped while the request converts its body, and then the request continues
        ServiceHelper.stopService(producer);
        continueConverting.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "The exchange should be completed");

        assertEquals(0, replyManagers.size(), "No reply manager should be created after the producer stopped");
        assertInstanceOf(RejectedExecutionException.class, exchange.getException());
    }

    @Test
    void testReplyManagerCreatedWhileRunning() throws Exception {
        // control (passes without the change): a running producer creates its reply manager once, and the stop of the
        // producer stops it
        startProducer();

        producer.initReplyManager();
        producer.initReplyManager();
        assertEquals(1, replyManagers.size(), "The reply manager should be created once");
        assertTrue(replyManagers.get(0).isStarted());

        ServiceHelper.stopService(producer);
        assertTrue(replyManagers.get(0).isStopped(), "The stop of the producer should stop its reply manager");
    }

    private void startProducer() {
        producer = new RocketMQProducer((RocketMQEndpoint) context.getEndpoint(ROCKETMQ_URI)) {
            @Override
            protected ReplyManager createReplyManager() {
                // the real reply manager starts a consumer of the reply topic, which needs a name server
                TestReplyManager replyManager = new TestReplyManager();
                ServiceHelper.startService(replyManager);
                replyManagers.add(replyManager);
                return replyManager;
            }
        };
        ServiceHelper.startService(producer);
    }

    private static final class SlowBody {
    }

    private static final class TestReplyManager extends ServiceSupport implements ReplyManager {

        @Override
        public void setEndpoint(RocketMQEndpoint endpoint) {
        }

        @Override
        public void setReplyToTopic(String replyToTopic) {
        }

        @Override
        public String registerReply(
                ReplyManager replyManager, Exchange exchange, AsyncCallback callback, String messageKey,
                long requestTimeout) {
            return messageKey;
        }

        @Override
        public void setScheduledExecutorService(ScheduledExecutorService executorService) {
        }

        @Override
        public void processReply(ReplyHolder holder) {
        }

        @Override
        public void cancelMessageKey(String messageKey) {
        }
    }
}
