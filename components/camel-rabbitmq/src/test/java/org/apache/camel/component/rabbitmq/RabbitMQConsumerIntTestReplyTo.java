/**
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
package org.apache.camel.component.rabbitmq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration test to check if requested direct reply messages are received
 */
public class RabbitMQConsumerIntTestReplyTo extends AbstractRabbitMQIntTest {

    private static final String EXCHANGE = "ex_reply";
    private static final String ROUTING_KEY = "testreply";
    private static final String REQUEST = "Knock! Knock!";
    private static final String REPLY = "Hello world";
    private static final String QUEUE = "amq.rabbitmq.reply-to";

    @EndpointInject(uri = "rabbitmq:localhost:5672/" + EXCHANGE + "?routingKey=" + ROUTING_KEY)
    private Endpoint from;

    private Connection connection;
    private Channel channel;

    @Before
    public void setUpRabbitMQ() throws Exception {
        connection = connection();
        channel = connection.createChannel();
//        channel.queueDeclare("sammyq", false, false, true, null);
//        channel.queueBind("sammyq", EXCHANGE, ROUTE);
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        context().setTracing(true);
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                log.info("Building routes...");
                
                from(from)
                        .log(body().toString())
                        .setBody(simple(REPLY));
            }
        };
    }

    @Test
    public void replyMessageIsReceived() throws IOException, TimeoutException, InterruptedException {
        final List<String> received = new ArrayList<>();
        
        AMQP.BasicProperties.Builder prop = new AMQP.BasicProperties.Builder();
        prop.replyTo(QUEUE);
        
        channel.basicConsume(QUEUE, true, new ArrayPopulatingConsumer(received));
        channel.basicPublish(EXCHANGE, ROUTING_KEY, prop.build(), REQUEST.getBytes());
        
        assertThatBodiesReceivedIn(received, REPLY);
    }
    
    private void assertThatBodiesReceivedIn(final List<String> received, final String... expected) throws InterruptedException {
        Thread.sleep(500);

        assertListSize(received, expected.length);
        for (String body : expected) {
            assertEquals(body, received.get(0));
        }
    }
    
    private class ArrayPopulatingConsumer extends DefaultConsumer {
        private final List<String> received;

        ArrayPopulatingConsumer(final List<String> received) {
            super(RabbitMQConsumerIntTestReplyTo.this.channel);
            this.received = received;
        }

        @Override
        public void handleDelivery(String consumerTag,
                                   Envelope envelope,
                                   AMQP.BasicProperties properties,
                                   byte[] body) throws IOException {
            received.add(new String(body));
        }
    }

}

