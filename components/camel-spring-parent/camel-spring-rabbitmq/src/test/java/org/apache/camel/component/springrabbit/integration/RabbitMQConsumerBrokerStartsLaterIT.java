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

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The broker is not reachable when Camel starts: the route starts, and the consumer declares the exchange, queue and
 * binding when its listener container connects.
 */
public class RabbitMQConsumerBrokerStartsLaterIT extends RabbitMQITSupport {

    private CachingConnectionFactory laterConnectionFactory;
    private CachingConnectionFactory testConnectionFactory;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // nothing listens on this port yet
        laterConnectionFactory = new CachingConnectionFactory("localhost", closedPort());
        context.getRegistry().bind("laterCF", laterConnectionFactory);
        return context;
    }

    @AfterEach
    void destroyConnectionFactories() {
        // stop the consumer before its connection factory
        context.stop();
        if (testConnectionFactory != null) {
            testConnectionFactory.destroy();
        }
        if (laterConnectionFactory != null) {
            laterConnectionFactory.destroy();
        }
    }

    @Test
    public void testConsumerDeclaresWhenTheBrokerIsReachable() throws Exception {
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("consumer"));

        // the broker is reachable now
        laterConnectionFactory.setHost(service.hostname());
        laterConnectionFactory.setPort(service.port());

        testConnectionFactory = (CachingConnectionFactory) createConnectionFactory(false);
        RabbitAdmin admin = new RabbitAdmin(testConnectionFactory);
        await().atMost(60, TimeUnit.SECONDS).until(() -> consumers(admin, uniqueName("orders-queue")) > 0);

        getMockEndpoint("mock:result").expectedBodiesReceived("order 1");
        new RabbitTemplate(testConnectionFactory).convertAndSend(uniqueName("orders"), "new", "order 1");

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    private static int consumers(RabbitAdmin admin, String queue) {
        Properties properties = admin.getQueueProperties(queue);
        return properties == null ? 0 : ((Number) properties.get(RabbitAdmin.QUEUE_CONSUMER_COUNT)).intValue();
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("spring-rabbitmq:" + uniqueName("orders") + "?connectionFactory=#laterCF&queues="
                     + uniqueName("orders-queue") + "&routingKey=new")
                        .routeId("consumer")
                        .to("mock:result");
            }
        };
    }
}
