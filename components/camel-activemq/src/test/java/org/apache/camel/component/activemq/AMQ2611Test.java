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

import junit.framework.TestCase;

import org.apache.activemq.broker.BrokerService;
import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AMQ2611Test extends TestCase {

    private static final String BROKER_URL = "tcp://localhost:61616";
    private static final String QUEUE_NAME = "test.queue";
    private static final Logger LOG = LoggerFactory.getLogger(AMQ2611Test.class);
    private BrokerService brokerService;
    private CamelContext camelContext;

    private void createBroker() throws Exception {
        brokerService = new BrokerService();
        brokerService.addConnector(BROKER_URL);
        brokerService.start();
    }

    public static class Consumer {
        public void consume(@Body String message) {
            LOG.info("consume message = " + message);
        }
    }

    private void createCamelContext() throws Exception {
        LOG.info("creating context and sending message");
        camelContext = new DefaultCamelContext();
        camelContext.addComponent("activemq", ActiveMQComponent.activeMQComponent(BROKER_URL));
        final String queueEndpointName = "activemq:queue" + QUEUE_NAME;
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(queueEndpointName).bean(Consumer.class, "consume");
            }
        });
        camelContext.start();
        final ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        producerTemplate.sendBody(queueEndpointName, "message");
    }

    private void destroyCamelContext() throws Exception {
        LOG.info("destroying context");
        camelContext.stop();
        camelContext = null;
    }

    public void testConnections() {
        try {
            createBroker();
            int i = 0;
            while (i++ < 5) {
                createCamelContext();
                Thread.sleep(1000);
                destroyCamelContext();
                Thread.sleep(1000);
                assertEquals(0, brokerService.getConnectorByName(BROKER_URL).getConnections().size());
            }
        } catch (Exception e) {
            LOG.warn("run", e);
        }
    }
}
