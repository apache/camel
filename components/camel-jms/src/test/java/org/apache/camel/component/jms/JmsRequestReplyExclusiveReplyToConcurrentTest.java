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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated("Creates multiple threads")
public class JmsRequestReplyExclusiveReplyToConcurrentTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    private static final Logger LOG = LoggerFactory.getLogger(JmsRequestReplyExclusiveReplyToConcurrentTest.class);
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    private final int size = 100;
    private final CountDownLatch latch = new CountDownLatch(size);
    private ExecutorService executor;

    @BeforeEach
    void setUpExecutor() {
        executor = Executors.newFixedThreadPool(10);
    }

    @AfterEach
    void cleanupExecutor() {
        executor.shutdown();
        try {
            final boolean finished = executor.awaitTermination(1, TimeUnit.SECONDS);
            if (!finished) {
                LOG.debug("Executor tasks did not terminate within the timeout (shutdown will be forced)");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    @Test
    public void testJmsRequestReplyExclusiveFixedReplyTo() throws Exception {
        StopWatch watch = new StopWatch();

        for (int i = 0; i < size; i++) {
            final Integer num = i;
            executor.submit(() -> {
                String reply = template.requestBody("direct:start", "" + num, String.class);
                LOG.info("Sent {} expecting reply 'Hello {}' got --> {}", num, num, reply);
                assertNotNull(reply);
                assertEquals("Hello " + num, reply);
                latch.countDown();
            });
        }

        LOG.info("Waiting to process {} messages...", size);

        // if any of the assertions above fails then the latch will not get decremented
        assertTrue(latch.await(20, TimeUnit.SECONDS), "All assertions outside the main thread above should have passed");

        long delta = watch.taken();
        LOG.info("Took {} millis", delta);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("activemq:queue:fooJmsRequestReplyExclusiveReplyToConcurrentTest?replyTo=JmsRequestReplyExclusiveReplyToConcurrentTest.bar&replyToType=Exclusive&concurrentConsumers=5&maxConcurrentConsumers=10&maxMessagesPerTask=100")
                        .to("log:reply")
                        .to("mock:reply");

                from("activemq:queue:fooJmsRequestReplyExclusiveReplyToConcurrentTest?concurrentConsumers=5&maxConcurrentConsumers=10&maxMessagesPerTask=100")
                        .transform(body().prepend("Hello "));
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }
}
