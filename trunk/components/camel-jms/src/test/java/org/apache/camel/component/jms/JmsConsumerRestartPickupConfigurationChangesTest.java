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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class JmsConsumerRestartPickupConfigurationChangesTest extends CamelTestSupport {

    @Test
    public void testRestartJmsConsumerPickupChanges() throws Exception {
        JmsEndpoint endpoint = context.getEndpoint("activemq:queue:foo", JmsEndpoint.class);
        JmsConsumer consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                template.send("mock:result", exchange);
            }
        });

        consumer.start();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello World");
        template.sendBody("activemq:queue:foo", "Hello World");
        assertMockEndpointsSatisfied();

        consumer.stop();

        // change to listen on another queue
        endpoint.setDestinationName("bar");
        endpoint.setConcurrentConsumers(2);

        // restart it
        consumer.start();

        result.reset();
        result.expectedBodiesReceived("Bye World");
        template.sendBody("activemq:queue:bar", "Bye World");
        assertMockEndpointsSatisfied();

        consumer.stop();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

}
