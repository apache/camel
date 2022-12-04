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

import java.nio.charset.StandardCharsets;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.rocketmq.infra.EmbeddedRocketMQServer;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.namesrv.NamesrvController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RocketMQRequestReplyRouteTest extends CamelTestSupport {

    private static final int NAMESRV_PORT = 59877;

    private static final String NAMESRV_ADDR = "127.0.0.1:" + NAMESRV_PORT;

    private static final String START_ENDPOINT_URI = "rocketmq:START_TOPIC?producerGroup=p1&consumerGroup=c1";

    private static final String INTERMEDIATE_ENDPOINT_URI = "rocketmq:INTERMEDIATE_TOPIC" +
                                                            "?producerGroup=intermediaProducer" +
                                                            "&consumerGroup=intermediateConsumer" +
                                                            "&replyToTopic=REPLY_TO_TOPIC" +
                                                            "&replyToConsumerGroup=replyToConsumerGroup" +
                                                            "&requestTimeoutMillis=30000";

    private static final String RESULT_ENDPOINT_URI = "mock:result";

    private static final String EXPECTED_MESSAGE = "Hi.";

    private static NamesrvController namesrvController;

    private static BrokerController brokerController;

    private MockEndpoint resultEndpoint;

    private DefaultMQPushConsumer replierConsumer;

    private DefaultMQProducer replierProducer;

    @BeforeAll
    static void beforeAll() throws Exception {
        namesrvController = EmbeddedRocketMQServer.createAndStartNamesrv(NAMESRV_PORT);
        brokerController = EmbeddedRocketMQServer.createAndStartBroker(NAMESRV_ADDR);
        EmbeddedRocketMQServer.createTopic(NAMESRV_ADDR, "DefaultCluster", "START_TOPIC");
        EmbeddedRocketMQServer.createTopic(NAMESRV_ADDR, "DefaultCluster", "INTERMEDIATE_TOPIC");
        EmbeddedRocketMQServer.createTopic(NAMESRV_ADDR, "DefaultCluster", "REPLY_TO_TOPIC");
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        resultEndpoint = (MockEndpoint) context.getEndpoint(RESULT_ENDPOINT_URI);
        replierProducer = new DefaultMQProducer("replierProducer");
        replierProducer.setNamesrvAddr(NAMESRV_ADDR);
        replierProducer.start();
        replierConsumer = new DefaultMQPushConsumer("replierConsumer");
        replierConsumer.setNamesrvAddr(NAMESRV_ADDR);
        replierConsumer.subscribe("INTERMEDIATE_TOPIC", "*");
        replierConsumer.registerMessageListener((MessageListenerConcurrently) (msgs, unused) -> {
            MessageExt messageExt = msgs.get(0);
            String key = messageExt.getKeys();
            Message response = new Message("REPLY_TO_TOPIC", "", key, EXPECTED_MESSAGE.getBytes(StandardCharsets.UTF_8));
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
        rocketMQComponent.setNamesrvAddr(NAMESRV_ADDR);
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
        resultEndpoint.expectedBodiesReceived(EXPECTED_MESSAGE);
        resultEndpoint.message(0).header(RocketMQConstants.TOPIC).isEqualTo("REPLY_TO_TOPIC");

        template.sendBody(START_ENDPOINT_URI, "hello, RocketMQ.");

        resultEndpoint.assertIsSatisfied();
    }

    @AfterEach
    public void tearDown() {
        replierConsumer.shutdown();
        replierProducer.shutdown();
    }

    @AfterAll
    public static void afterAll() {
        brokerController.shutdown();
        namesrvController.shutdown();
    }
}
