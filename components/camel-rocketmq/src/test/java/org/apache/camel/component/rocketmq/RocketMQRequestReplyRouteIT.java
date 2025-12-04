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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@DisabledIfSystemProperty(
        named = "ci.env.name",
        matches = ".*",
        disabledReason = "These tests are flaky and unreliable - see CAMEL-19832")
public class RocketMQRequestReplyRouteIT extends RocketMQTestSupport {

    private static final String START_ENDPOINT_URI = "rocketmq:START_TOPIC_RRT?producerGroup=p1&consumerGroup=c1";

    private static final String INTERMEDIATE_ENDPOINT_URI =
            "rocketmq:INTERMEDIATE_TOPIC" + "?producerGroup=intermediaProducer"
                    + "&consumerGroup=intermediateConsumer"
                    + "&replyToTopic=REPLY_TO_TOPIC"
                    + "&replyToConsumerGroup=replyToConsumerGroup"
                    + "&requestTimeoutMillis=30000";

    private static final String RESULT_ENDPOINT_URI = "mock:result";

    private static final String EXPECTED_MESSAGE = "Hi.";

    private static final int MESSAGE_COUNT = 5;

    private DefaultMQPushConsumer replierConsumer;

    private DefaultMQProducer replierProducer;

    @BeforeAll
    static void beforeAll() throws Exception {
        rocketMQService.createTopic("START_TOPIC_RRT");
        rocketMQService.createTopic("INTERMEDIATE_TOPIC");
        rocketMQService.createTopic("REPLY_TO_TOPIC");
    }

    @Override
    @BeforeEach
    public void doPostSetup() throws Exception {
        replierProducer = new DefaultMQProducer("replierProducer");
        replierProducer.setNamesrvAddr(rocketMQService.nameserverAddress());
        replierProducer.start();
        replierConsumer = new DefaultMQPushConsumer("replierConsumer");
        replierConsumer.setNamesrvAddr(rocketMQService.nameserverAddress());
        replierConsumer.subscribe("INTERMEDIATE_TOPIC", "*");
        replierConsumer.registerMessageListener((MessageListenerConcurrently) (msgs, unused) -> {
            MessageExt messageExt = msgs.get(0);
            String key = messageExt.getKeys();
            Message response =
                    new Message("REPLY_TO_TOPIC", "", key, EXPECTED_MESSAGE.getBytes(StandardCharsets.UTF_8));
            try {
                replierProducer.send(response);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        replierConsumer.start();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        RocketMQComponent rocketMQComponent = new RocketMQComponent();
        rocketMQComponent.setNamesrvAddr(rocketMQService.nameserverAddress());
        camelContext.addComponent("rocketmq", rocketMQComponent);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from(START_ENDPOINT_URI)
                        .to(ExchangePattern.InOut, INTERMEDIATE_ENDPOINT_URI)
                        .to(RESULT_ENDPOINT_URI);
            }
        };
    }

    @Test
    public void testRouteMessageInRequestReplyMode() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint(RESULT_ENDPOINT_URI);
        // It is very slow, so we are lenient and OK if we receive just 1 message
        resultEndpoint.expectedMinimumMessageCount(1);
        resultEndpoint.message(0).header(RocketMQConstants.TOPIC).isEqualTo("REPLY_TO_TOPIC");

        for (int i = 0; i < MESSAGE_COUNT; i++) {
            template.sendBody(START_ENDPOINT_URI, "hello, RocketMQ.");
        }

        resultEndpoint.assertIsSatisfied();
    }

    @Override
    public void doPostTearDown() {
        replierConsumer.shutdown();
        replierProducer.shutdown();
    }

    @AfterAll
    public static void afterAll() throws IOException, InterruptedException {
        rocketMQService.deleteTopic("START_TOPIC_RRT");
        rocketMQService.deleteTopic("INTERMEDIATE_TOPIC");
        rocketMQService.deleteTopic("REPLY_TO_TOPIC");
    }
}
