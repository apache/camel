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

import jakarta.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.common.ConnectionFactoryHelper;
import org.apache.camel.throttling.ThrottlingInflightRoutePolicy;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentTransacted;

public class JmsThrottlingInflightRoutePolicyTest extends AbstractPersistentJMSTest {

    @Test
    public void testJmsThrottlingInflightRoutePolicy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        int size = 200;
        mock.expectedMinimumMessageCount(size);

        for (int i = 0; i < size; i++) {
            template.sendBody("activemq-sender:queue:JmsThrottlingInflightRoutePolicyTest", "Message " + i);
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                ThrottlingInflightRoutePolicy policy = new ThrottlingInflightRoutePolicy();
                policy.setMaxInflightExchanges(10);
                policy.setResumePercentOfMax(50);
                policy.setScope(ThrottlingInflightRoutePolicy.ThrottlingScope.Route);

                from("activemq:queue:JmsThrottlingInflightRoutePolicyTest?concurrentConsumers=20").routePolicy(policy)
                        .delay(100)
                        .to("log:foo?groupSize=10").to("mock:result");
            }
        };
    }

    @Override
    protected void createConnectionFactory(CamelContext camelContext) {
        ConnectionFactory connectionFactory = ConnectionFactoryHelper.createConnectionFactory(service);
        camelContext.addComponent("activemq", jmsComponentTransacted(connectionFactory));

        // and use another component for sender
        camelContext.addComponent("activemq-sender", jmsComponentTransacted(connectionFactory));
    }
}
