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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.activemq.AMQ2611Test.Consumer;
import org.apache.camel.component.activemq.support.ActiveMQSupport;
import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class AMQ2240Test implements ActiveMQSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AMQ2240Test.class);

    private CamelContext camelContext;

    @AfterEach
    public void destroyCamelContext() {
        if (camelContext != null) {
            camelContext.stop();
            camelContext = null;
        }
    }

    @Test
    public void testBadVMTransportOptionsJMSPrefix() {
        final String vmUri
                = vmUri("?jms.redeliveryPolicy.maximumRedeliveries=0&"
                        + "jms.redeliveryPolicy.initialRedeliveryDelay=500&"
                        + "jms.useAsyncSend=false&jms.sendTimeout=ABC&"
                        + "jms.maxXXXXReconnectAttempts=1&jms.timeout=3000");

        LOG.info("creating context with bad URI: {}", vmUri);
        ActiveMQComponent amq = ActiveMQComponent.activeMQComponent(vmUri);

        final JmsConfiguration configuration = amq.getConfiguration();
        assertThrows(IllegalArgumentException.class, () -> configuration.getOrCreateConnectionFactory(),
                "Should have received an exception from the bad URI.");
    }

    @Test
    public void testBadVMTransportOptionsBrokerPrefix() throws Exception {
        final String vmUri = vmUri("?broker.XXX=foo&broker.persistent=XXX&broker.useJmx=false");

        LOG.info("creating context with bad URI: {}", vmUri);
        ActiveMQComponent amq = ActiveMQComponent.activeMQComponent(vmUri);

        camelContext = new DefaultCamelContext();
        camelContext.addComponent("activemq", amq);
        final String queueEndpointName = "activemq:queuetest.Queue";
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(queueEndpointName).bean(Consumer.class, "consume");
            }
        });

        camelContext.start();
        final ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        assertThrows(CamelExecutionException.class, () -> producerTemplate.sendBody(queueEndpointName, "message"),
                "Should have received an exception from the bad URI.");
    }
}
