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
package org.apache.camel.component.sjms.producer;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.jmx.DestinationViewMBean;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.Assume;
import org.junit.Test;

public class QueueProducerQoSTest extends JmsTestSupport {

    private static final String TEST_INONLY_DESTINATION_NAME = "queue.producer.test.qos.inonly";
    private static final String TEST_INOUT_DESTINATION_NAME = "queue.producer.test.qos.inout";

    private static final String EXPIRED_MESSAGE_ROUTE_ID = "expiredAdvisoryRoute";
    private static final String MOCK_EXPIRED_ADVISORY = "mock:expiredAdvisory";

    @EndpointInject(MOCK_EXPIRED_ADVISORY)
    MockEndpoint mockExpiredAdvisory;

    @Test
    public void testInOutQueueProducerTTL() throws Exception {
        Assume.assumeFalse(externalAmq);
        mockExpiredAdvisory.expectedMessageCount(1);

        String endpoint = String.format("sjms:queue:%s?ttl=1000&exchangePattern=InOut&responseTimeOut=500", TEST_INOUT_DESTINATION_NAME);

        try {
            template.requestBody(endpoint, "test message");
            fail("we aren't expecting any consumers, so should not succeed");
        } catch (Exception e) {
            // we are expecting an exception here because there are no consumers on this queue,
            // so we will not be able to do a real InOut/request-response, but that's okay
            // we're just interested in the message becoming expired
        }

        assertMockEndpointsSatisfied();

        DestinationViewMBean queue = getQueueMBean(TEST_INOUT_DESTINATION_NAME);
        assertEquals("There were unexpected messages left in the queue: " + TEST_INOUT_DESTINATION_NAME,
                0, queue.getQueueSize());
    }

    @Test
    public void testInOnlyQueueProducerTTL() throws Exception {
        Assume.assumeFalse(externalAmq);
        mockExpiredAdvisory.expectedMessageCount(1);

        String endpoint = String.format("sjms:queue:%s?ttl=1000", TEST_INONLY_DESTINATION_NAME);
        template.sendBody(endpoint, "test message");

        assertMockEndpointsSatisfied();

        DestinationViewMBean queue = getQueueMBean(TEST_INONLY_DESTINATION_NAME);
        assertEquals("There were unexpected messages left in the queue: " + TEST_INONLY_DESTINATION_NAME,
                0, queue.getQueueSize());
    }

    @Override
    protected void configureBroker(BrokerService broker) throws Exception {
        broker.setUseJmx(true);
        broker.setPersistent(true);
        broker.setDataDirectory("target/activemq-data");
        broker.deleteAllMessages();
        broker.setAdvisorySupport(true);
        broker.addConnector(brokerUri);

        // configure expiration rate
        ActiveMQQueue queueName = new ActiveMQQueue(">");
        PolicyEntry entry = new PolicyEntry();
        entry.setDestination(queueName);
        entry.setExpireMessagesPeriod(1000);

        PolicyMap policyMap = new PolicyMap();
        policyMap.put(queueName, entry);
        broker.setDestinationPolicy(policyMap);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("sjms:topic:ActiveMQ.Advisory.Expired.Queue.>")
                        .routeId(EXPIRED_MESSAGE_ROUTE_ID)
                        .to(MOCK_EXPIRED_ADVISORY);
            }
        };
    }
}
