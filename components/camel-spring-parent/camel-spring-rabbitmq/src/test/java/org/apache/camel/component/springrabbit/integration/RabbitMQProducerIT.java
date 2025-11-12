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
package org.apache.camel.component.springrabbit.integration;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.springrabbit.SpringRabbitMQConstants;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class RabbitMQProducerIT extends RabbitMQITSupport {
    private static final Logger LOG = LoggerFactory.getLogger(RabbitMQProducerIT.class);

    @Disabled("Load test used to verify producer performance CAMEL-22278")
    @Test
    public void testProducerLoadAndReceive() throws Exception {
        // --- 1. AMQP Setup ---
        // Ensures the RabbitMQ queue, exchange, and binding exist before the test.
        ConnectionFactory cf = context.getRegistry().lookupByNameAndType("myCF", ConnectionFactory.class);
        Queue q = new Queue("myqueue");
        TopicExchange t = new TopicExchange("foo");
        AmqpAdmin admin = new RabbitAdmin(cf);
        admin.declareQueue(q);
        admin.declareExchange(t);
        admin.declareBinding(BindingBuilder.bind(q).to(t).with("foo.bar.#"));

        // --- 2. Test Configuration ---
        final int numThreads = 10;
        final int totalDurationSeconds = 10;
        final int snapshotTimeSeconds = 5;

        // --- 3. Concurrency Tools ---
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final AtomicInteger messageCounter = new AtomicInteger(0);
        final AtomicBoolean running = new AtomicBoolean(true);

        // --- 4. Define the Message Sending Task ---
        Runnable senderTask = () -> {
            while (running.get()) {
                template.sendBody("direct:start", "Load test message #" + messageCounter.get());
                messageCounter.incrementAndGet();
            }
        };

        // --- 5. Start the Load Test ---
        LOG.info("Starting load test with {} threads for {} seconds...", numThreads, totalDurationSeconds);
        for (int i = 0; i < numThreads; i++) {
            executor.submit(senderTask);
        }

        // --- 6. Wait and Count at 5 Seconds ---
        Thread.sleep(TimeUnit.SECONDS.toMillis(snapshotTimeSeconds));

        int countAfter5Seconds = messageCounter.get();
        LOG.info(">>> Messages sent after {} seconds: {}", snapshotTimeSeconds, countAfter5Seconds);
        Assertions.assertTrue(countAfter5Seconds > 0, "No messages were sent after 5 seconds.");

        // --- 7. Wait for the Remaining Time ---
        Thread.sleep(TimeUnit.SECONDS.toMillis(totalDurationSeconds - snapshotTimeSeconds));

        // --- 8. Stop Threads and Gather Final Sent Count ---
        LOG.info("Stopping threads...");
        running.set(false);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        int finalSentCount = messageCounter.get();
        LOG.info(">>> Total messages sent in {} seconds: {}", totalDurationSeconds, finalSentCount);

        // --- 9. NEW: Receive All Messages from the Queue ---
        LOG.info("\n--- Draining and Verifying Queue Content ---");
        AmqpTemplate rabbitConsumerTemplate = new RabbitTemplate(cf);

        int receivedCount = 0;
        StopWatch lastMessageStopWatch = new StopWatch();
        final long idleTimeoutMillis = 2000; // 2 seconds
        while (true) {
            Object receivedMessage = rabbitConsumerTemplate.receiveAndConvert("myqueue");

            if (receivedMessage != null) {
                // If we got a message, increment count and reset the idle timer.
                receivedCount++;
                lastMessageStopWatch.restart();
            } else {
                // If we got no message, check if the idle timeout has been exceeded.
                if (lastMessageStopWatch.taken() > idleTimeoutMillis) {
                    LOG.info(
                            "No messages received for " + (idleTimeoutMillis / 1000) + " seconds. Exiting consumer loop.");
                    break; // Exit the loop
                }
            }
        }
        LOG.info("Total messages received from queue: " + receivedCount);

        // --- 10. Final Verification ---
        Assertions.assertEquals(finalSentCount, receivedCount, "The number of sent and received messages should match.");
    }

    @Test
    public void testProducer() throws Exception {
        ConnectionFactory cf = context.getRegistry().lookupByNameAndType("myCF", ConnectionFactory.class);

        Queue q = new Queue("myqueue");
        TopicExchange t = new TopicExchange("foo");

        AmqpAdmin admin = new RabbitAdmin(cf);
        admin.declareQueue(q);
        admin.declareExchange(t);
        admin.declareBinding(BindingBuilder.bind(q).to(t).with("foo.bar.#"));

        template.sendBody("direct:start", "Hello World");

        AmqpTemplate template = new RabbitTemplate(cf);
        String out = (String) template.receiveAndConvert("myqueue");
        Assertions.assertEquals("Hello World", out);
    }

    @Test
    public void testProducerWithHeader() throws Exception {
        ConnectionFactory cf = context.getRegistry().lookupByNameAndType("myCF", ConnectionFactory.class);

        Queue q = new Queue("myqueue");
        TopicExchange t = new TopicExchange("foo");

        AmqpAdmin admin = new RabbitAdmin(cf);
        admin.declareQueue(q);
        admin.declareExchange(t);
        admin.declareBinding(BindingBuilder.bind(q).to(t).with("foo.bar.#"));

        template.sendBodyAndHeader("direct:start", "Hello World", "cheese", "gouda");

        AmqpTemplate template = new RabbitTemplate(cf);
        Message out = template.receive("myqueue");

        byte[] body = out.getBody();
        Assertions.assertNotNull(body, "The body should not be null");
        Assertions.assertEquals("Hello World", new String(body));
        Assertions.assertEquals("gouda", out.getMessageProperties().getHeader("cheese"));
    }

    @Test
    public void testProducerWithMessage() throws Exception {
        ConnectionFactory cf = context.getRegistry().lookupByNameAndType("myCF", ConnectionFactory.class);

        Queue q = new Queue("myqueue");
        TopicExchange t = new TopicExchange("foo");

        AmqpAdmin admin = new RabbitAdmin(cf);
        admin.declareQueue(q);
        admin.declareExchange(t);
        admin.declareBinding(BindingBuilder.bind(q).to(t).with("foo.bar.#"));

        MessageProperties props = MessagePropertiesBuilder.newInstance()
                .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                .setMessageId("123")
                .setHeader("bar", "baz")
                .build();
        Message body = MessageBuilder.withBody("foo".getBytes())
                .andProperties(props)
                .build();

        template.sendBody("direct:start", body);

        AmqpTemplate template = new RabbitTemplate(cf);
        Message out = template.receive("myqueue");
        Assertions.assertEquals("foo", new String(out.getBody()));
        Assertions.assertEquals("baz", out.getMessageProperties().getHeader("bar"));
    }

    @Test
    public void testProducerWithMessageProperties() throws Exception {
        ConnectionFactory cf = context.getRegistry().lookupByNameAndType("myCF", ConnectionFactory.class);

        Queue q = new Queue("myqueue");
        TopicExchange t = new TopicExchange("foo");

        AmqpAdmin admin = new RabbitAdmin(cf);
        admin.declareQueue(q);
        admin.declareExchange(t);
        admin.declareBinding(BindingBuilder.bind(q).to(t).with("foo.bar.#"));

        template.sendBodyAndHeaders("direct:start", "<price>123</price>",
                Map.of(SpringRabbitMQConstants.DELIVERY_MODE, MessageDeliveryMode.PERSISTENT,
                        SpringRabbitMQConstants.TYPE, "price",
                        SpringRabbitMQConstants.CONTENT_TYPE, "application/xml",
                        SpringRabbitMQConstants.MESSAGE_ID, "0fe9c142-f9c1-426f-9237-f5a4c988a8ae",
                        SpringRabbitMQConstants.PRIORITY, 1));

        AmqpTemplate template = new RabbitTemplate(cf);
        Message out = template.receive("myqueue");

        final MessageProperties messageProperties = out.getMessageProperties();
        Assertions.assertNotNull(messageProperties, "The message properties should not be null");
        String encoding = messageProperties.getContentEncoding();
        Assertions.assertEquals(Charset.defaultCharset().name(), encoding);
        Assertions.assertEquals("<price>123</price>", new String(out.getBody(), encoding));
        Assertions.assertEquals(MessageDeliveryMode.PERSISTENT, messageProperties.getReceivedDeliveryMode());
        Assertions.assertEquals("price", messageProperties.getType());
        Assertions.assertEquals("application/xml", messageProperties.getContentType());
        Assertions.assertEquals("0fe9c142-f9c1-426f-9237-f5a4c988a8ae", messageProperties.getMessageId());
        Assertions.assertEquals(1, messageProperties.getPriority());
        Assertions.assertEquals(0, messageProperties.getHeaders().size());
    }

    @Test
    public void testProducerWithBreadcrumb() throws Exception {
        ConnectionFactory cf = context.getRegistry().lookupByNameAndType("myCF", ConnectionFactory.class);

        Queue q = new Queue("myqueue");
        TopicExchange t = new TopicExchange("foo");

        AmqpAdmin admin = new RabbitAdmin(cf);
        admin.declareQueue(q);
        admin.declareExchange(t);
        admin.declareBinding(BindingBuilder.bind(q).to(t).with("foo.bar.#"));

        template.sendBodyAndHeaders("direct:start", "<price>123</price>",
                Map.of(SpringRabbitMQConstants.DELIVERY_MODE, MessageDeliveryMode.PERSISTENT,
                        SpringRabbitMQConstants.TYPE, "price", Exchange.BREADCRUMB_ID, "mycrumb123"));

        AmqpTemplate template = new RabbitTemplate(cf);
        Message out = template.receive("myqueue");

        final MessageProperties messageProperties = out.getMessageProperties();
        Assertions.assertNotNull(messageProperties, "The message properties should not be null");
        String encoding = messageProperties.getContentEncoding();
        Assertions.assertEquals(Charset.defaultCharset().name(), encoding);
        Assertions.assertEquals("<price>123</price>", new String(out.getBody(), encoding));
        Assertions.assertEquals(MessageDeliveryMode.PERSISTENT, messageProperties.getReceivedDeliveryMode());
        Assertions.assertEquals("price", messageProperties.getType());
        Assertions.assertEquals("mycrumb123", messageProperties.getHeader(Exchange.BREADCRUMB_ID));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("spring-rabbitmq:foo?routingKey=foo.bar");
            }
        };
    }
}
