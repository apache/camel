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
package org.apache.camel.component.activemq;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.activemq.support.ActiveMQSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedService;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedServiceBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AMQ2611Test implements ActiveMQSupport {
    private static final String QUEUE_NAME = "test.queue";
    private static final Logger LOG = LoggerFactory.getLogger(AMQ2611Test.class);

    @RegisterExtension
    public ActiveMQEmbeddedService service = ActiveMQEmbeddedServiceBuilder
            .defaultBroker()
            .withTcpTransport()
            .build();

    private CamelContext camelContext;
    private CountDownLatch startedLatch = new CountDownLatch(1);
    private CountDownLatch stoppedLatch = new CountDownLatch(1);

    public static class Consumer {
        public void consume(@Body String message) {
            LOG.info("consume message = {}", message);
        }
    }

    private void createCamelContext() throws Exception {
        LOG.info("creating context and sending message");
        camelContext = new DefaultCamelContext();
        camelContext.addComponent("activemq", ActiveMQComponent.activeMQComponent(service.serviceAddress()));
        final String queueEndpointName = "activemq:queue" + QUEUE_NAME;
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(queueEndpointName).bean(Consumer.class, "consume");
            }
        });

        camelContext.addLifecycleStrategy(new LifecycleStrategySupport() {
            @Override
            public void onContextStarted(CamelContext context) {
                super.onContextStarted(context);
                startedLatch.countDown();
            }

            @Override
            public void onContextStopped(CamelContext context) {
                super.onContextStopped(context);
                stoppedLatch.countDown();
            }
        });

        camelContext.start();
        final ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        producerTemplate.sendBody(queueEndpointName, "message");
    }

    private void destroyCamelContext() {
        LOG.info("destroying context");
        camelContext.stop();
        camelContext = null;
    }

    @Test
    public void testConnections() {
        try {
            int i = 0;
            while (i++ < 5) {
                createCamelContext();
                startedLatch.await(1, TimeUnit.SECONDS);
                destroyCamelContext();
                stoppedLatch.await(1, TimeUnit.SECONDS);
                assertEquals(0, service.getConnectionCount());
            }
        } catch (Exception e) {
            LOG.warn("run", e);
        }
    }
}
