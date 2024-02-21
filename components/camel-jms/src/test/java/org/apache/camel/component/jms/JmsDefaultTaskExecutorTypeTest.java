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
package org.apache.camel.component.jms;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test cannot run in parallel: running this test in parallel messes with the number of threads in the pools,
 * resulting in failures.
 */
@Tags({ @Tag("not-parallel"), @Tag("slow") })
@Timeout(60)
class JmsDefaultTaskExecutorTypeTest extends CamelTestSupport {
    public static final int MESSAGE_COUNT = 500;
    public static final int POOL_SIZE = 5;

    private static final Logger LOG = LoggerFactory.getLogger(JmsDefaultTaskExecutorTypeTest.class);

    @RegisterExtension
    public ArtemisService service = ArtemisServiceFactory.createPersistentVMService();

    @Test
    void testThreadPoolTaskExecutor() throws Exception {
        context.getRouteController().startRoute("threadPool");
        Long beforeThreadCount = currentThreadCount();
        getMockEndpoint("mock:result.threadPool").expectedMessageCount(1000);
        doSendMessages("foo.JmsDefaultTaskExecutorTypeTest.threadPool", DefaultTaskExecutorType.ThreadPool);
        doSendMessages("foo.JmsDefaultTaskExecutorTypeTest.threadPool", DefaultTaskExecutorType.ThreadPool);
        MockEndpoint.assertIsSatisfied(context, 40, TimeUnit.SECONDS);
        long numberThreadsCreated = currentThreadCount() - beforeThreadCount;
        LOG.info("Number of threads created, testThreadPoolTaskExecutor: {}", numberThreadsCreated);
        assertTrue(numberThreadsCreated <= 100, "Number of threads created should be equal or lower than "
                                                + "100 with ThreadPoolTaskExecutor: " + numberThreadsCreated);
    }

    @Test
    void testSimpleAsyncTaskExecutor() throws Exception {
        context.getRouteController().startRoute("simpleAsync");
        Long beforeThreadCount = currentThreadCount();
        getMockEndpoint("mock:result.simpleAsync").expectedMessageCount(1000);
        doSendMessages("foo.JmsDefaultTaskExecutorTypeTest.simpleAsync", DefaultTaskExecutorType.SimpleAsync);
        doSendMessages("foo.JmsDefaultTaskExecutorTypeTest.simpleAsync", DefaultTaskExecutorType.SimpleAsync);
        MockEndpoint.assertIsSatisfied(context, 40, TimeUnit.SECONDS);
        long numberThreadsCreated = currentThreadCount() - beforeThreadCount;
        LOG.info("Number of threads created, testSimpleAsyncTaskExecutor: {}", numberThreadsCreated);
        assertTrue(numberThreadsCreated >= 800, "Number of threads created should be equal or higher than "
                                                + "800 with SimpleAsyncTaskExecutor: " + numberThreadsCreated);
    }

    @Test
    void testDefaultTaskExecutor() throws Exception {
        context.getRouteController().startRoute("default");
        Long beforeThreadCount = currentThreadCount();
        getMockEndpoint("mock:result.default").expectedMessageCount(1000);
        doSendMessages("foo.JmsDefaultTaskExecutorTypeTest.default", null);
        doSendMessages("foo.JmsDefaultTaskExecutorTypeTest.default", null);
        MockEndpoint.assertIsSatisfied(context, 40, TimeUnit.SECONDS);
        long numberThreadsCreated = currentThreadCount() - beforeThreadCount;
        LOG.info("Number of threads created, testDefaultTaskExecutor: {}", numberThreadsCreated);
        assertTrue(numberThreadsCreated >= 800, "Number of threads created should be equal or higher than "
                                                + "800 with default behaviour: " + numberThreadsCreated);
    }

    @Test
    void testDefaultTaskExecutorThreadPoolAtComponentConfig() throws Exception {
        // change the config of the component
        context.getComponent("jms", JmsComponent.class).getConfiguration()
                .setDefaultTaskExecutorType(DefaultTaskExecutorType.ThreadPool);

        context.getRouteController().startRoute("default");
        Long beforeThreadCount = currentThreadCount();
        getMockEndpoint("mock:result.default").expectedMessageCount(1000);
        doSendMessages("foo.JmsDefaultTaskExecutorTypeTest.default", DefaultTaskExecutorType.ThreadPool);
        doSendMessages("foo.JmsDefaultTaskExecutorTypeTest.default", DefaultTaskExecutorType.ThreadPool);
        MockEndpoint.assertIsSatisfied(context, 40, TimeUnit.SECONDS);
        long numberThreadsCreated = currentThreadCount() - beforeThreadCount;
        LOG.info("Number of threads created, testDefaultTaskExecutorThreadPoolAtComponentConfig: {}", numberThreadsCreated);
        assertTrue(numberThreadsCreated <= 100, "Number of threads created should be equal or lower than "
                                                + "100 with ThreadPoolTaskExecutor as a component default");
    }

    private Long currentThreadCount()
            throws NoSuchMethodException,
            IllegalAccessException, InvocationTargetException {
        Method m = ThreadHelper.class.getDeclaredMethod("nextThreadCounter", (Class<?>[]) null);
        m.setAccessible(true);
        return (Long) m.invoke(null);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = ConnectionFactoryHelper.createConnectionFactory(service);
        JmsComponent jmsComponent = jmsComponentAutoAcknowledge(connectionFactory);
        jmsComponent.getConfiguration().setMaxMessagesPerTask(1);
        jmsComponent.getConfiguration().setIdleTaskExecutionLimit(1);
        jmsComponent.getConfiguration().setConcurrentConsumers(3);
        jmsComponent.getConfiguration().setMaxConcurrentConsumers(10);
        jmsComponent.getConfiguration().setReceiveTimeout(50);
        camelContext.addComponent("activemq", jmsComponent);
        return camelContext;
    }

    private void doSendMessages(
            final String queueName, int messages,
            final DefaultTaskExecutorType defaultTaskExecutorType, CountDownLatch latch, ExecutorService executor) {

        for (int i = 0; i < messages; i++) {
            final int index = i;
            executor.submit(() -> {
                String options = defaultTaskExecutorType == null
                        ? "" : "?defaultTaskExecutorType=" + defaultTaskExecutorType;
                template.requestBody("activemq:queue:" + queueName + options, "Message " + index);
                latch.countDown();
                return null;
            });
        }
    }

    private void doSendMessages(
            final String queueName,
            final DefaultTaskExecutorType defaultTaskExecutorType)
            throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE);
        final CountDownLatch latch = new CountDownLatch(MESSAGE_COUNT);

        try {
            doSendMessages(queueName, MESSAGE_COUNT, defaultTaskExecutorType, latch, executor);
            executor.shutdown();
            executor.awaitTermination(POOL_SIZE, TimeUnit.SECONDS);
        } finally {
            latch.await(POOL_SIZE, TimeUnit.SECONDS);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:foo.JmsDefaultTaskExecutorTypeTest.simpleAsync?defaultTaskExecutorType=SimpleAsync")
                        .routeId("simpleAsync")
                        .noAutoStartup()
                        .to("mock:result.simpleAsync")
                        .setBody(constant("Reply"));

                from("activemq:queue:foo.JmsDefaultTaskExecutorTypeTest.threadPool?defaultTaskExecutorType=ThreadPool")
                        .routeId("threadPool").noAutoStartup()
                        .to("mock:result.threadPool")
                        .setBody(constant("Reply"));

                from("activemq:queue:foo.JmsDefaultTaskExecutorTypeTest.default").routeId("default").noAutoStartup()
                        .to("mock:result.default")
                        .setBody(constant("Reply"));
            }
        };
    }
}
