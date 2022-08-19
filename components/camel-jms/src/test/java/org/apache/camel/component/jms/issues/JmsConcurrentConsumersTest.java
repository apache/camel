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
package org.apache.camel.component.jms.issues;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrent consumer with JMSReply test.
 */
@Isolated
public class JmsConcurrentConsumersTest extends AbstractJMSTest {

    @Test
    public void testConcurrentConsumersWithReply() throws Exception {
        // latch for the 5 exchanges we expect
        final CountDownLatch latch = new CountDownLatch(5);

        // setup a task executor to be able send the messages in parallel
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.afterPropertiesSet();
        for (int i = 0; i < 5; i++) {
            final int count = i;
            executor.execute(() -> {
                // request body is InOut pattern and thus we expect a reply (JMSReply)
                Object response = template.requestBody("activemq:a", "World #" + count);
                assertEquals("Bye World #" + count, response);
                latch.countDown();
            });
        }

        long start = System.currentTimeMillis();

        // wait for test completion, timeout after 30 sec to let other unit test run to not wait forever
        assertTrue(latch.await(30000L, TimeUnit.MILLISECONDS));
        assertEquals(0, latch.getCount(), "Latch should be zero");

        long delta = System.currentTimeMillis() - start;
        assertTrue(delta < 20000L, "Should be faster than 20000 millis, took " + delta + " millis");
        executor.shutdown();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory
                = createConnectionFactory(service);
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:a?concurrentConsumers=3").to("activemq:b?concurrentConsumers=3");

                from("activemq:b?concurrentConsumers=3")
                        .delay(Duration.ofSeconds(3).toMillis())
                        .transform(body().prepend("Bye ")).to("log:reply");
            }
        };
    }

}
