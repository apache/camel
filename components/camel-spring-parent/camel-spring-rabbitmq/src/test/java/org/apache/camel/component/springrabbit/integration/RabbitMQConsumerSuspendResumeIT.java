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

import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.springrabbit.SpringRabbitMQComponent;
import org.apache.camel.component.springrabbit.SpringRabbitMQConstants;
import org.apache.camel.component.springrabbit.SpringRabbitMQConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route that is suspended and resumed consumes again, with either type of listener container: while it is suspended,
 * the queue has no consumer and keeps the message sent meanwhile, which is consumed once the route is resumed.
 */
public class RabbitMQConsumerSuspendResumeIT extends RabbitMQITSupport {

    // how long a listener container waits for the messages it is processing when it stops: longer than the
    // shutdownAwaitTermination of the test, which the consumer waits for the listener container on top of it
    private static final long SHUTDOWN_TIMEOUT = 2000;
    private static final long SHUTDOWN_AWAIT_TERMINATION = 1000;

    // the exchanges and queues of this test run only, so that another run on the same broker, or a message that a
    // failed run left in a queue, does not change the counts
    private final String run = UUID.randomUUID().toString();
    // the test checks the queue with its own connection factory
    private CachingConnectionFactory testConnectionFactory;
    // how long the route that suspends its own consumer waited for the suspend
    private final AtomicLong suspendTime = new AtomicLong();
    // the messages that the routes which suspend their own consumer received
    private final List<String> selfReceived = new CopyOnWriteArrayList<>();

    @AfterEach
    void destroyTestConnectionFactory() {
        if (testConnectionFactory != null) {
            testConnectionFactory.destroy();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "DMLC", "SMLC" })
    public void testSuspendAndResume(String type) throws Exception {
        String queue = name(type + "-queue");
        testConnectionFactory = (CachingConnectionFactory) createConnectionFactory(false);
        RabbitAdmin admin = new RabbitAdmin(testConnectionFactory);
        MockEndpoint mock = getMockEndpoint("mock:" + type);
        mock.expectedBodiesReceived("order 1");
        template.sendBody("direct:" + type, "order 1");
        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);

        context.getRouteController().suspendRoute(type);

        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus(type));
        mock.reset();
        mock.expectedBodiesReceived("order 2");
        template.sendBody("direct:" + type, "order 2");
        // the message waits in the queue, which has no consumer
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> count(admin, queue, RabbitAdmin.QUEUE_MESSAGE_COUNT) == 1
                        && count(admin, queue, RabbitAdmin.QUEUE_CONSUMER_COUNT) == 0);

        context.getRouteController().resumeRoute(type);

        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus(type));
        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    /**
     * A route that suspends its own consumer while it processes a message, as a route policy does when an exchange is
     * done: the listener container waits its whole shutdownTimeout for that message, and the suspend still ends in time
     * (no WARN), as the consumer waits for the shutdownTimeout plus the shutdownAwaitTermination. The listener
     * container then closes the channel of that message, so the broker delivers it again once the consumer is resumed.
     */
    @ParameterizedTest
    @ValueSource(strings = { "DMLC", "SMLC" })
    public void testSuspendFromTheListenerThread(String type) throws Exception {
        String route = "self-" + type;
        List<LogEvent> warnings = new CopyOnWriteArrayList<>();
        AbstractAppender appender = new AbstractAppender(
                "suspendFromTheListenerThread", null, null, true,
                Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.WARN) {
                    warnings.add(event.toImmutable());
                }
            }
        };
        appender.start();
        Logger logger = (Logger) LogManager.getLogger(SpringRabbitMQConsumer.class);
        logger.addAppender(appender);
        try {
            template.sendBody("direct:" + route, "order 1");
            await().atMost(30, TimeUnit.SECONDS).until(() -> suspendTime.get() > 0);
            Consumer consumer = context.getRoute(route).getConsumer();

            assertTrue(ServiceHelper.isSuspended(consumer), "the consumer is suspended");
            assertTrue(TimeUnit.NANOSECONDS.toMillis(suspendTime.get()) >= SHUTDOWN_TIMEOUT - 100,
                    "the listener container waited its shutdownTimeout for the message: " + suspendTime.get());
            assertEquals(List.of(), warnings.stream().map(e -> e.getMessage().getFormattedMessage())
                    .filter(m -> m.contains(name(route + "-queue"))).toList(), "no WARN");

            ServiceHelper.resumeService(consumer);
            template.sendBody("direct:" + route, "order 2");
            await().atMost(30, TimeUnit.SECONDS).until(() -> selfReceived.size() == 3);
            assertEquals(List.of(route + ":order 1:redelivered=false", route + ":order 1:redelivered=true",
                    route + ":order 2:redelivered=false"), selfReceived);
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getExecutorServiceManager().setShutdownAwaitTermination(SHUTDOWN_AWAIT_TERMINATION);
        context.getComponent("spring-rabbitmq", SpringRabbitMQComponent.class).setShutdownTimeout(SHUTDOWN_TIMEOUT);
        return context;
    }

    private String name(String baseName) {
        return uniqueName(baseName) + "-" + run;
    }

    private static int count(RabbitAdmin admin, String queue, Object property) {
        Properties properties = admin.getQueueProperties(queue);
        return properties == null ? -1 : ((Number) properties.get(property)).intValue();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                for (String type : new String[] { "DMLC", "SMLC" }) {
                    from("direct:" + type)
                            .to("spring-rabbitmq:" + name(type) + "?routingKey=new");

                    from("spring-rabbitmq:" + name(type) + "?queues=" + name(type + "-queue")
                         + "&routingKey=new&messageListenerContainerType=" + type)
                            .routeId(type)
                            .to("mock:" + type);

                    String self = "self-" + type;
                    from("direct:" + self)
                            .to("spring-rabbitmq:" + name(self) + "?routingKey=new");

                    from("spring-rabbitmq:" + name(self) + "?queues=" + name(self + "-queue")
                         + "&routingKey=new&messageListenerContainerType=" + type)
                            .routeId(self)
                            .process(exchange -> {
                                String body = exchange.getMessage().getBody(String.class);
                                selfReceived.add(self + ":" + body + ":redelivered="
                                                 + exchange.getMessage().getHeader(SpringRabbitMQConstants.REDELIVERED));
                                if ("order 1".equals(body) && suspendTime.get() == 0) {
                                    long start = System.nanoTime();
                                    ServiceHelper.suspendService(exchange.getContext().getRoute(self).getConsumer());
                                    suspendTime.set(System.nanoTime() - start);
                                }
                            });
                }
            }
        };
    }
}
