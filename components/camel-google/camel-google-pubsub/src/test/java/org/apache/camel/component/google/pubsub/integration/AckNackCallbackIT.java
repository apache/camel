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
package org.apache.camel.component.google.pubsub.integration;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.PubsubTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test that validates the ACK/NACK callback behavior for synchronous pull.
 *
 * This test ensures that: 1. Messages are properly NACK'd on failure (exception thrown) and redelivered 2. Messages are
 * properly ACK'd on successful processing
 *
 * The fix in CamelMessageReceiver and GooglePubsubConsumer ensures that OnCompletion synchronization callbacks are
 * properly triggered after message processing, which was previously not happening.
 */
public class AckNackCallbackIT extends PubsubTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(AckNackCallbackIT.class);

    private static final String TOPIC_NAME = "ackNackCallbackTopic";
    private static final String SUBSCRIPTION_NAME = "ackNackCallbackSub";

    // Counter to track how many times a message has been processed
    private static final AtomicInteger processCount = new AtomicInteger(0);

    // Flag to control failure behavior - fail on first attempt only
    private static volatile boolean shouldFail = false;

    @EndpointInject("direct:in")
    private Endpoint directIn;

    @EndpointInject("google-pubsub:{{project.id}}:" + TOPIC_NAME)
    private Endpoint pubsubTopic;

    @EndpointInject("google-pubsub:{{project.id}}:" + SUBSCRIPTION_NAME + "?synchronousPull=true")
    private Endpoint pubsubSubscription;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Produce("direct:in")
    private ProducerTemplate producer;

    @Override
    public void createTopicSubscription() {
        // Create topic/subscription pair with short ack deadline for faster test execution
        createTopicSubscriptionPair(TOPIC_NAME, SUBSCRIPTION_NAME, 10);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // Producer route
                from(directIn).routeId("Producer").to(pubsubTopic);

                // Consumer route - can be configured to fail on first attempt
                from(pubsubSubscription)
                        .routeId("Consumer")
                        .autoStartup(true)
                        .process(exchange -> {
                            int count = processCount.incrementAndGet();
                            LOG.info("Consumer processing attempt #{}", count);

                            if (shouldFail && count == 1) {
                                LOG.info("Consumer throwing exception on first attempt (will trigger NACK)");
                                throw new RuntimeException("Simulated failure on first attempt");
                            }
                            LOG.info("Consumer processing successful on attempt #{}", count);
                        })
                        .to(result);
            }
        };
    }

    /**
     * Test that validates both NACK/redelivery and ACK behavior.
     *
     * Stage 1: Send a message that will fail on first attempt - Message is received and processed (first attempt) -
     * Exception thrown, NACK sent - Message redelivered by PubSub - Message received and processed successfully (second
     * attempt) - ACK sent, message removed from subscription
     *
     * Stage 2: Send a message that succeeds on first attempt - Message is received and processed successfully - ACK
     * sent immediately, no redelivery
     */
    @Test
    public void testAckNackCallbackBehavior() throws Exception {
        // === Stage 1: Test NACK and redelivery ===
        LOG.info("Stage 1: Testing NACK on failure and redelivery");

        processCount.set(0);
        shouldFail = true;

        Exchange failExchange = new DefaultExchange(context);
        failExchange.getIn().setBody("Test message for NACK/redelivery: " + failExchange.getExchangeId());

        // We expect the message to eventually succeed after redelivery
        result.expectedMessageCount(1);

        // Send the message
        producer.send(failExchange);

        // Wait for the message to be processed (may take up to ack deadline + processing time)
        result.assertIsSatisfied(15000);

        // Verify that the message was processed at least twice (first attempt failed, second succeeded)
        int nackProcessCount = processCount.get();
        LOG.info("Stage 1: Consumer processed message {} times", nackProcessCount);
        assertTrue(nackProcessCount >= 2,
                "Message should have been processed at least twice due to NACK/redelivery, but was processed "
                                          + nackProcessCount + " times");

        // === Stage 2: Test immediate ACK on success ===
        LOG.info("Stage 2: Testing immediate ACK on success");

        result.reset();
        processCount.set(0);
        shouldFail = false;

        Exchange successExchange = new DefaultExchange(context);
        successExchange.getIn().setBody("Test message for immediate ACK: " + successExchange.getExchangeId());

        result.expectedMessageCount(1);

        // Send the message
        producer.send(successExchange);

        // Wait for message to be processed
        result.assertIsSatisfied(5000);

        // Wait a bit more to ensure no redelivery happens
        Thread.sleep(3000);

        // Verify message was processed exactly once (properly ACK'd, no redelivery)
        int ackProcessCount = processCount.get();
        LOG.info("Stage 2: Consumer processed message {} times (expecting 1)", ackProcessCount);
        assertTrue(ackProcessCount == 1,
                "Message should have been processed exactly once (properly ACK'd), but was processed "
                                         + ackProcessCount + " times");

        LOG.info("All stages passed - ACK/NACK callback behavior is working correctly");
    }
}
