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

import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unit test for creating jms endpoint manually
 */
public class JmsRouteEndpointTest extends CamelTestSupport {

    private JmsEndpoint jms;

    @Test
    public void testRouteToFile() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        template.sendBody(jms, "Hello World");

        result.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");

                jms = new JmsEndpoint();
                jms.setCamelContext(context);
                jms.setDestinationName("queue:hello");
                jms.setConnectionFactory(connectionFactory);
                JmsConfiguration config = new JmsConfiguration();
                config.setConnectionFactory(connectionFactory);
                config.setAcknowledgementMode(Session.CLIENT_ACKNOWLEDGE);
                jms.setConfiguration(config);

                from(jms).to("mock:result");
            }
        };
    }
}