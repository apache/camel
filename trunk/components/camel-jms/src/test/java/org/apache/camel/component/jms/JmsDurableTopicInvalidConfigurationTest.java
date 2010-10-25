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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;

/**
 * @version $Revision: 992224 $
 */
public class JmsDurableTopicInvalidConfigurationTest extends CamelTestSupport {

    @Override
    public void setUp() throws Exception {
        deleteDirectory("./activemq-data");
        super.setUp();
    }

    @Test
    public void testDurableTopicInvalidConfiguration() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("activemq:topic:foo?durableSubscriptionName=bar")
                        .to("mock:result");
                }
            });
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("ClientId must be configured when subscription is durable for"
                + " Endpoint[activemq://topic:foo?durableSubscriptionName=bar]", e.getMessage());
        }
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=true");
        camelContext.addComponent("activemq", jmsComponentClientAcknowledge(connectionFactory));
        return camelContext;
    }

}