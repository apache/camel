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
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class RabbitMQConsumerIntTest extends CamelTestSupport {

    private static final String EXCHANGE = "ex1";

    @EndpointInject(uri = "rabbitmq:localhost:5672/" + EXCHANGE + "?username=cameltest&password=cameltest")
    private Endpoint from;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint to;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from(from).to(to);
            }
        };
    }

    @Test
    public void sentMessageIsReceived() throws InterruptedException, IOException, TimeoutException {

        to.expectedMessageCount(1);
        to.expectedHeaderReceived(RabbitMQConstants.REPLY_TO, "myReply");

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername("cameltest");
        factory.setPassword("cameltest");
        factory.setVirtualHost("/");
        Connection conn = factory.newConnection();

        AMQP.BasicProperties.Builder properties = new AMQP.BasicProperties.Builder();
        properties.replyTo("myReply");

        Channel channel = conn.createChannel();
        channel.basicPublish(EXCHANGE, "", properties.build(), "hello world".getBytes());

        to.assertIsSatisfied();
    }
}

