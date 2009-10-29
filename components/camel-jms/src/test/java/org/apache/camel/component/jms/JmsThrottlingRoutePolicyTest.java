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

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.ThrottlingRoutePolicy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentTransacted;

/**
 * @version $Revision$
 */
public class JmsThrottlingRoutePolicyTest extends CamelTestSupport {

    private int size = 200;

    @Override
    public void setUp() throws Exception {
        deleteDirectory("activemq-data");
        super.setUp();
    }

    @Test
    public void testJmsThrottlingRoutePolicy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(size);

        for (int i = 0; i < size; i++) {
            template.sendBody("activemq-sender:queue:foo", "Message " + i);
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                ThrottlingRoutePolicy policy = new ThrottlingRoutePolicy();
                policy.setMaxInflightExchanges(10);
                policy.setResumePercentOfMax(50);
                policy.setScope(ThrottlingRoutePolicy.ThrottlingScope.Route);

                from("activemq:queue:foo?concurrentConsumers=20").routePolicy(policy)
                        .delay(100)
                        .to("log:foo?groupSize=10").to("mock:result");
            }
        };
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=true");
        camelContext.addComponent("activemq", jmsComponentTransacted(connectionFactory));

        // and use another component for sender
        camelContext.addComponent("activemq-sender", jmsComponentTransacted(connectionFactory));

        return camelContext;
    }

}