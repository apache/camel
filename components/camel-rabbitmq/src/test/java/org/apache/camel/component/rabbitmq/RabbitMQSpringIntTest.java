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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.CamelSpringJUnit4ClassRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import static org.junit.Assert.assertEquals;
/**
 * Test RabbitMQ component with Spring DSL
 */
@RunWith(CamelSpringJUnit4ClassRunner.class)
@ContextConfiguration("RabbitMQSpringIntTest-context.xml")
public class RabbitMQSpringIntTest {
    @Produce(uri = "direct:rabbitMQ")
    protected ProducerTemplate template;
    @Autowired
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Channel channel;

    private Connection openConnection() throws IOException {
        if (connection == null) {
            connection = connectionFactory.newConnection();
        }
        return connection;
    }

    private Channel openChannel() throws IOException {
        if (channel == null) {
            channel = openConnection().createChannel();
        }
        return channel;
    }

    @Before
    public void bindQueueExchange() throws IOException {
        openChannel();
        channel.exchangeDeclare("ex2", "direct", true, false, null);
        channel.queueDeclare("q2", true, false, false, null);
        channel.queueBind("q2", "ex2", "rk2");
    }

    @After
    public void closeConnection() {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
            }
        }
    }

    private static final class LastDeliveryConsumer extends DefaultConsumer {
        private byte[] lastBody;

        private LastDeliveryConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            lastBody = body;
            super.handleDelivery(consumerTag, envelope, properties, body);
        }

        public byte[] getLastBody() {
            return lastBody;
        }
    }

    @Test
    public void testSendCsutomConnectionFactory() throws Exception {
        String body = "Hello Rabbit";
        template.sendBodyAndHeader(body, RabbitMQConstants.ROUTING_KEY, "rk2");

        openChannel();
        LastDeliveryConsumer consumer = new LastDeliveryConsumer(channel);
        channel.basicConsume("q2", true, consumer);
        int i = 10;
        while (consumer.getLastBody() == null && i > 0) {
            Thread.sleep(1000L);
            i--;
        }
        assertEquals(body, new String(consumer.getLastBody()));
    }
}
