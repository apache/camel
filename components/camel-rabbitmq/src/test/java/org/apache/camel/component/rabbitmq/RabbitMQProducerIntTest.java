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
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;


public class RabbitMQProducerIntTest extends CamelTestSupport {
    private static final String EXCHANGE = "ex1";

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    
    @EndpointInject(uri = "rabbitmq:localhost:5672/" + EXCHANGE + "?routingKey=route1&username=cameltest&password=cameltest")
    private Endpoint to;

    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:start").to(to);
            }
        };
    }

    @Test
    public void producedMessageIsReceived() throws InterruptedException, IOException, TimeoutException {

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername("cameltest");
        factory.setPassword("cameltest");
        factory.setVirtualHost("/");
        Connection conn = factory.newConnection();

        final List<Envelope> received = new ArrayList<Envelope>();

        Channel channel = conn.createChannel();
        channel.queueDeclare("sammyq", false, false, true, null);
        channel.queueBind("sammyq", EXCHANGE, "route1");
        channel.basicConsume("sammyq", true, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag,
                                       Envelope envelope,
                                       AMQP.BasicProperties properties,
                                       byte[] body) throws IOException {
                received.add(envelope);
            }
        });

        template.sendBodyAndHeader("new message", RabbitMQConstants.EXCHANGE_NAME, "ex1");
        Thread.sleep(500);
        assertEquals(1, received.size());
    }
}

