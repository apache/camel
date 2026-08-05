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
package org.apache.camel.component.sjms.producer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.TextMessage;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InOutQueueProducerAsyncLoadTest extends JmsTestSupport {

    private static final String TEST_DESTINATION_NAME = "in.out.queue.producer.test.InOutQueueProducerAsyncLoadTest";
    private static final int MESSAGE_COUNT = 500;
    private static final long LATCH_TIMEOUT_SECONDS = 60;
    private static final long EXECUTOR_SHUTDOWN_SECONDS = 10;
    private static final long INFLIGHT_TIMEOUT_SECONDS = 30;

    private MessageConsumer mc1;
    private MessageConsumer mc2;
    private final AtomicInteger listenerErrors = new AtomicInteger();

    @BeforeEach
    void setupConsumers() throws Exception {
        listenerErrors.set(0);
        mc1 = createQueueConsumer(TEST_DESTINATION_NAME + ".request");
        mc2 = createQueueConsumer(TEST_DESTINATION_NAME + ".request");
        mc1.setMessageListener(new MyMessageListener());
        mc2.setMessageListener(new MyMessageListener());
    }

    @AfterEach
    void cleanupConsumers() throws JMSException {
        MyMessageListener l1 = (MyMessageListener) mc1.getMessageListener();
        l1.close();
        mc1.close();
        MyMessageListener l2 = (MyMessageListener) mc2.getMessageListener();
        l2.close();
        mc2.close();
    }

    /**
     * Test to verify that when using the consumer listener for the InOut producer we get the correct message back.
     */
    @Test
    void testInOutQueueProducer() throws Exception {
        final CountDownLatch latch = new CountDownLatch(MESSAGE_COUNT);
        final AtomicInteger failures = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 1; i <= MESSAGE_COUNT; i++) {
                final int tempI = i;
                executor.execute(() -> {
                    try {
                        final String requestText = "Message " + tempI;
                        final String responseText = "Response Message " + tempI;
                        String response = template.requestBody("direct:start", requestText, String.class);
                        assertNotNull(response);
                        assertEquals(responseText, response);
                    } catch (Exception e) {
                        failures.incrementAndGet();
                        log.error("Failed to process message {}", tempI, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertTrue(latch.await(LATCH_TIMEOUT_SECONDS, SECONDS), "Not all messages were processed within the timeout");
            assertEquals(0, failures.get(), "Some messages failed during processing");
            assertEquals(0, listenerErrors.get(), "Some JMS listener callbacks failed");
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(EXECUTOR_SHUTDOWN_SECONDS, SECONDS), "Executor did not terminate in time");
        }

        // async route completion can lag behind request/reply futures — poll until inflight is drained
        await().atMost(INFLIGHT_TIMEOUT_SECONDS, SECONDS)
                .untilAsserted(() -> assertEquals(0, context.getInflightRepository().size()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("log:" + TEST_DESTINATION_NAME + ".in.log?showBody=true")
                        .to(ExchangePattern.InOut, "sjms:queue:" + TEST_DESTINATION_NAME + ".request" + "?replyTo="
                                                   + TEST_DESTINATION_NAME
                                                   + ".response&concurrentConsumers=10")
                        .threads(20)
                        .to("log:" + TEST_DESTINATION_NAME + ".out.log?showBody=true");
            }
        };
    }

    protected class MyMessageListener implements MessageListener {
        private MessageProducer mp;

        @Override
        public void onMessage(Message message) {
            try {
                TextMessage request = (TextMessage) message;
                String text = request.getText();

                TextMessage response = getSession().createTextMessage();
                response.setText("Response " + text);
                response.setJMSCorrelationID(request.getJMSCorrelationID());
                if (mp == null) {
                    mp = getSession().createProducer(message.getJMSReplyTo());
                }
                mp.send(response);
            } catch (JMSException e) {
                listenerErrors.incrementAndGet();
                log.error("Failed to process JMS message in test listener", e);
            }
        }

        public void close() throws JMSException {
            if (mp != null) {
                mp.close();
            }
        }
    }
}
