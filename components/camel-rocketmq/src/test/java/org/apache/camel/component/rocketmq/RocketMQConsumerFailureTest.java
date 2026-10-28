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
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The status returned to the RocketMQ push consumer acknowledges the message: a message whose route failed must be
 * consumed again, not acknowledged.
 */
public class RocketMQConsumerFailureTest extends CamelTestSupport {

    private static final String ROCKETMQ_URI = "rocketmq:START_TOPIC?namesrvAddr=localhost:9876&consumerGroup=c1";

    @Test
    public void testFailedExchangeIsConsumedLater() throws Exception {
        RocketMQConsumer consumer = createConsumer("direct:fail");

        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER, consumer.consumeMessage(List.of(message()), null));
    }

    @Test
    public void testExceptionFromProcessorIsConsumedLater() throws Exception {
        RocketMQEndpoint endpoint = context.getEndpoint(ROCKETMQ_URI, RocketMQEndpoint.class);
        RocketMQConsumer consumer = (RocketMQConsumer) endpoint.createConsumer(exchange -> {
            throw new IllegalStateException("Forced");
        });

        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER, consumer.consumeMessage(List.of(message()), null));
    }

    @Test
    public void testRollbackOnlyExchangeIsConsumedLater() throws Exception {
        RocketMQConsumer consumer = createConsumer("direct:rollback");

        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER, consumer.consumeMessage(List.of(message()), null));
    }

    @Test
    public void testCompletedExchangeIsAcknowledged() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello");

        RocketMQConsumer consumer = createConsumer("direct:ok");

        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS, consumer.consumeMessage(List.of(message()), null));
        result.assertIsSatisfied();
    }

    private RocketMQConsumer createConsumer(String route) throws Exception {
        RocketMQEndpoint endpoint = context.getEndpoint(ROCKETMQ_URI, RocketMQEndpoint.class);
        // the route sets the exception on the exchange when it fails, as the route of a consumer does
        return (RocketMQConsumer) endpoint.createConsumer(exchange -> template.send(route, exchange));
    }

    private static MessageExt message() {
        MessageExt messageExt = new MessageExt();
        messageExt.setTopic("START_TOPIC");
        messageExt.setBody("Hello".getBytes(StandardCharsets.UTF_8));
        return messageExt;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:fail")
                        .throwException(new IllegalStateException("Forced"));

                from("direct:rollback")
                        .markRollbackOnly();

                from("direct:ok")
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }
}
