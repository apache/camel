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
package org.apache.camel.component.activemq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.activemq.EmbeddedBrokerTestSupport;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BrowsableEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shows that we can see the queues inside ActiveMQ via Camel by enabling the
 * {@link ActiveMQComponent#setExposeAllQueues(boolean)} flag
 */
public class AutoExposeQueuesInCamelTest extends EmbeddedBrokerTestSupport {
    private static final transient Logger LOG = LoggerFactory.getLogger(AutoExposeQueuesInCamelTest.class);

    protected ActiveMQQueue sampleQueue = new ActiveMQQueue("foo.bar");
    protected ActiveMQTopic sampleTopic = new ActiveMQTopic("cheese");

    protected CamelContext camelContext = new DefaultCamelContext();
    ActiveMQComponent component;

    public void testWorks() throws Exception {
        Thread.sleep(2000);
        LOG.debug("Looking for endpoints...");
        broker.getAdminView().addQueue("runtime");

        Thread.sleep(1000);
        // Changed from using CamelContextHelper.getSingletonEndpoints here
        // because JMS Endpoints in Camel
        // are always non-singleton
        List<BrowsableEndpoint> endpoints = getEndpoints(camelContext, BrowsableEndpoint.class);
        for (BrowsableEndpoint endpoint : endpoints) {
            LOG.debug("Endpoint: " + endpoint);
        }
        assertEquals("Should have found an endpoint: " + endpoints, 2, endpoints.size());
    }

    public <T> List<T> getEndpoints(CamelContext camelContext, Class<T> type) {
        List<T> answer = new ArrayList<T>();
        Collection<Endpoint> endpoints = camelContext.getEndpoints();
        for (Endpoint endpoint : endpoints) {
            if (type.isInstance(endpoint)) {
                T value = type.cast(endpoint);
                answer.add(value);
            }
        }
        return answer;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // lets configure the ActiveMQ component for Camel
        component = new ActiveMQComponent();
        component.setBrokerURL(bindAddress);
        component.setExposeAllQueues(true);

        camelContext.addComponent("activemq", component);
        camelContext.start();
    }

    @Override
    protected void tearDown() throws Exception {
        camelContext.stop();
        super.tearDown();
    }

    @Override
    protected BrokerService createBroker() throws Exception {
        BrokerService broker = super.createBroker();
        broker.setDestinations(new ActiveMQDestination[] {sampleQueue, sampleTopic});
        return broker;
    }

}
