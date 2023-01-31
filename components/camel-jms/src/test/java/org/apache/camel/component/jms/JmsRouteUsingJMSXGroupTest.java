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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jms.JmsConstants.JMS_X_GROUP_ID;
import static org.apache.camel.test.junit5.TestSupport.body;

@Timeout(20)
public class JmsRouteUsingJMSXGroupTest extends AbstractJMSTest {
    public static final int POOL_SIZE = 1;
    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    private static final Logger LOG = LoggerFactory.getLogger(JmsRouteUsingJMSXGroupTest.class);
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    private ExecutorService executor;

    @Test
    public void testNoConcurrentProducersJMSXGroupID() throws Exception {
        doSendMessages(1);
    }

    @Test
    public void testConcurrentProducersJMSXGroupID() throws Exception {
        doSendMessages(10);
    }

    private void doSendMessages(int files) throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(files * 2);
        getMockEndpoint("mock:result").expectsNoDuplicates(body());

        for (int i = 0; i < files; i++) {
            final int index = i;
            executor.submit(() -> {
                template.sendBodyAndHeader("direct:start", "IBM: " + index, JMS_X_GROUP_ID, "IBM");
                template.sendBodyAndHeader("direct:start", "SUN: " + index, JMS_X_GROUP_ID, "SUN");

                return null;
            });
        }

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
    }

    @BeforeEach
    void setupExecutor() {
        executor = Executors.newFixedThreadPool(POOL_SIZE);
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

    @Override
    protected String getComponentName() {
        return "jms";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("jms:queue:JmsRouteUsingJMSXGroupTest");

                from("jms:queue:JmsRouteUsingJMSXGroupTest?concurrentConsumers=2").to("log:foo?showHeaders=false")
                        .to("mock:result");
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
