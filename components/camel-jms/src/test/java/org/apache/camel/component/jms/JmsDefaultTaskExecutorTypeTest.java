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
package org.apache.camel.component.jms;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 *
 */
public class JmsDefaultTaskExecutorTypeTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(JmsDefaultTaskExecutorTypeTest.class);
    @Rule public TestName name = new TestName();
    
    @Test
    public void testThreadPoolTaskExecutor() throws Exception {
        context.startRoute("threadPool");
        Long beforeThreadCount = currentThreadCount();
        getMockEndpoint("mock:result.threadPool").expectedMessageCount(1000);
        doSendMessages("foo.threadPool", 500, 5, DefaultTaskExecutorType.ThreadPool);
        Thread.sleep(100);
        doSendMessages("foo.threadPool", 500, 5, DefaultTaskExecutorType.ThreadPool);
        assertMockEndpointsSatisfied();
        Long numberThreadsCreated = currentThreadCount() - beforeThreadCount;
        LOG.info("Number of threads created, testThreadPoolTaskExecutor: " + numberThreadsCreated);
        assertTrue("Number of threads created should be equal or lower than " 
                + "100 with ThreadPoolTaskExecutor", numberThreadsCreated <= 100);
    }

    @Test
    public void testSimpleAsyncTaskExecutor() throws Exception {
        context.startRoute("simpleAsync");
        Long beforeThreadCount = currentThreadCount();
        getMockEndpoint("mock:result.simpleAsync").expectedMessageCount(1000);
        doSendMessages("foo.simpleAsync", 500, 5, DefaultTaskExecutorType.SimpleAsync);
        Thread.sleep(100);
        doSendMessages("foo.simpleAsync", 500, 5, DefaultTaskExecutorType.SimpleAsync);
        assertMockEndpointsSatisfied();
        Long numberThreadsCreated = currentThreadCount() - beforeThreadCount;
        LOG.info("Number of threads created, testSimpleAsyncTaskExecutor: " + numberThreadsCreated);
        assertTrue("Number of threads created should be equal or higher than " 
                + "800 with SimpleAsyncTaskExecutor", numberThreadsCreated >= 800);
    }

    @Test
    public void testDefaultTaskExecutor() throws Exception {
        context.startRoute("default");
        Long beforeThreadCount = currentThreadCount();
        getMockEndpoint("mock:result.default").expectedMessageCount(1000);
        doSendMessages("foo.default", 500, 5, null);
        Thread.sleep(100);
        doSendMessages("foo.default", 500, 5, null);
        assertMockEndpointsSatisfied();
        Long numberThreadsCreated = currentThreadCount() - beforeThreadCount;
        LOG.info("Number of threads created, testDefaultTaskExecutor: " + numberThreadsCreated);
        assertTrue("Number of threads created should be equal or higher than " 
                + "800 with default behaviour", numberThreadsCreated >= 800);
    }
    
    @Test
    public void testDefaultTaskExecutorThreadPoolAtComponentConfig() throws Exception {
        // the default behaviour changes in this test, see createCamelContext method below
        // the behaviour is the same as with testThreadPoolTaskExecutor test method above
        context.startRoute("default");
        Long beforeThreadCount = currentThreadCount();
        getMockEndpoint("mock:result.default").expectedMessageCount(1000);
        doSendMessages("foo.default", 500, 5, DefaultTaskExecutorType.ThreadPool);
        Thread.sleep(100);
        doSendMessages("foo.default", 500, 5, DefaultTaskExecutorType.ThreadPool);
        assertMockEndpointsSatisfied();
        Long numberThreadsCreated = currentThreadCount() - beforeThreadCount;
        LOG.info("Number of threads created, testDefaultTaskExecutorThreadPoolAtComponentConfig: " + numberThreadsCreated);
        assertTrue("Number of threads created should be equal or lower than " 
                + "100 with ThreadPoolTaskExecutor as a component default", numberThreadsCreated <= 100);
    }
    
    private Long currentThreadCount() throws NoSuchMethodException,
            IllegalAccessException, InvocationTargetException {
        Method m = ThreadHelper.class.getDeclaredMethod("nextThreadCounter", (Class<?>[]) null);
        m.setAccessible(true);
        Long nextThreadCount = (Long) m.invoke(null);
        return nextThreadCount;
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent jmsComponent = jmsComponentAutoAcknowledge(connectionFactory);
        jmsComponent.getConfiguration().setMaxMessagesPerTask(1);
        jmsComponent.getConfiguration().setIdleTaskExecutionLimit(1);
        jmsComponent.getConfiguration().setConcurrentConsumers(3);
        jmsComponent.getConfiguration().setMaxConcurrentConsumers(10);
        jmsComponent.getConfiguration().setReceiveTimeout(50);
        if ("testDefaultTaskExecutorThreadPoolAtComponentConfig".equals(getTestMethodName())) {
            jmsComponent.getConfiguration().setDefaultTaskExecutorType(DefaultTaskExecutorType.ThreadPool);
        }
        camelContext.addComponent("activemq", jmsComponent);
        return camelContext;
    }

    private void doSendMessages(final String queueName, int messages, int poolSize,
            final DefaultTaskExecutorType defaultTaskExecutorType) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        final CountDownLatch latch = new CountDownLatch(messages);
        for (int i = 0; i < messages; i++) {
            final int index = i;
            executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    String options = defaultTaskExecutorType == null ? "" : "?defaultTaskExecutorType=" 
                            + defaultTaskExecutorType.toString();
                    template.requestBody("activemq:queue:" + queueName + options, "Message " + index);
                    latch.countDown();
                    return null;
                }
            });
        }
        latch.await();
        executor.shutdown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:foo.simpleAsync?defaultTaskExecutorType=SimpleAsync").routeId("simpleAsync").noAutoStartup()
                    .to("mock:result.simpleAsync")
                    .setBody(constant("Reply"));

                from("activemq:queue:foo.threadPool?defaultTaskExecutorType=ThreadPool").routeId("threadPool").noAutoStartup()
                    .to("mock:result.threadPool")
                    .setBody(constant("Reply"));

                from("activemq:queue:foo.default").routeId("default").noAutoStartup()
                    .to("mock:result.default")
                    .setBody(constant("Reply"));
            }
        };
    }
}
