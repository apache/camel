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

import java.util.concurrent.Executors;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import static org.junit.jupiter.api.Assertions.assertNull;

public class RabbitMQPollingConsumerIT extends RabbitMQITSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        ConnectionFactory cf = context.getRegistry().lookupByNameAndType("myCF", ConnectionFactory.class);

        Queue q = new Queue("myqueue");
        DirectExchange t = new DirectExchange("foo");
        AmqpAdmin admin = new RabbitAdmin(cf);
        admin.declareQueue(q);
        admin.declareExchange(t);
        admin.declareBinding(BindingBuilder.bind(q).to(t).with("mykey"));

        return context;
    }

    @Test
    public void testJmsPollingConsumerWait() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Claus");

        // use another thread for polling consumer to demonstrate that we can wait before
        // the message is sent to the queue
        Executors.newSingleThreadExecutor().execute(() -> {
            String body = consumer.receiveBody("spring-rabbitmq:foo?queues=myqueue&routingKey=mykey", String.class);
            template.sendBody("spring-rabbitmq:foo?routingKey=mykey2", body + " Claus");
        });

        // wait a little to demonstrate we can start poll before we have a msg on the queue
        Thread.sleep(500);

        template.sendBody("direct:start", "Hello");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testJmsPollingConsumerLowTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Claus");

        // use another thread for polling consumer to demonstrate that we can wait before
        // the message is sent to the queue
        Executors.newSingleThreadExecutor().execute(() -> {
            String body = consumer.receiveBody("spring-rabbitmq:foo?queues=myqueue&routingKey=mykey", 100, String.class);
            assertNull(body, "Should be null");

            template.sendBody("spring-rabbitmq:foo?routingKey=mykey2", "Hello Claus");
        });

        // wait a little to demonstrate we can start poll before we have a msg on the queue
        Thread.sleep(500);

        template.sendBody("direct:start", "Hello");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testJmsPollingConsumerHighTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Claus");

        // use another thread for polling consumer to demonstrate that we can wait before
        // the message is sent to the queue
        Executors.newSingleThreadExecutor().execute(() -> {
            String body = consumer.receiveBody("spring-rabbitmq:foo?queues=myqueue&routingKey=mykey", 3000, String.class);
            template.sendBody("spring-rabbitmq:foo?routingKey=mykey2", body + " Claus");
        });

        // wait a little to demonstrate we can start poll before we have a msg on the queue
        Thread.sleep(500);

        template.sendBody("direct:start", "Hello");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").log("Sending ${body} to myqueue").to("spring-rabbitmq:foo?routingKey=mykey");

                from("spring-rabbitmq:foo?queues=myqueue2&routingKey=mykey2").log("Received ${body} from myqueue2")
                        .to("mock:result");
            }
        };
    }

}
