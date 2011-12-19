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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class JmsQosRouteTest extends CamelTestSupport {
    protected String componentName = "activemq";

    @Test
    public void testJmsRoutePreserveQos() throws Exception {
        
        MockEndpoint preserveEndpoint1 = context.getEndpoint("mock:preserve-1", MockEndpoint.class);
        preserveEndpoint1.expectedMessageCount(1);
        preserveEndpoint1.message(0).header("JMSPriority").isEqualTo(1);

        MockEndpoint preserveEndpoint2 = context.getEndpoint("mock:preserve-2", MockEndpoint.class);
        preserveEndpoint2.expectedMessageCount(1);
        preserveEndpoint2.message(0).header("JMSPriority").isEqualTo(2);

        template.sendBody(componentName + ":queue:p1?explicitQosEnabled=true&priority=1", "test");
        template.sendBody(componentName + ":queue:p2?explicitQosEnabled=true&priority=2", "test");

        MockEndpoint.assertIsSatisfied(preserveEndpoint1, preserveEndpoint2);
    }

    @Test
    public void testJmsRouteNormalQos() throws Exception {
        
        MockEndpoint regularEndpoint1 = context.getEndpoint("mock:regular-1", MockEndpoint.class);
        regularEndpoint1.expectedMessageCount(1);
        regularEndpoint1.message(0).header("JMSPriority").isEqualTo(4);

        MockEndpoint regularEndpoint2 = context.getEndpoint("mock:regular-2", MockEndpoint.class);
        regularEndpoint2.expectedMessageCount(1);
        regularEndpoint2.message(0).header("JMSPriority").isEqualTo(4);

        template.sendBody(componentName + ":queue:r1?explicitQosEnabled=true&priority=1", "test");
        template.sendBody(componentName + ":queue:r2?explicitQosEnabled=true&priority=2", "test");

        MockEndpoint.assertIsSatisfied(regularEndpoint1, regularEndpoint2);
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent(componentName, jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                
                // Messages should arrive at mock:preserve with their priorities preserved.
                from(componentName + ":queue:p1").to(componentName + ":queue:preserve-1?preserveMessageQos=true");
                from(componentName + ":queue:preserve-1").to("mock:preserve-1");
                from(componentName + ":queue:p2").to(componentName + ":queue:preserve-2?preserveMessageQos=true");
                from(componentName + ":queue:preserve-2").to("mock:preserve-2");

                // Messages will NOT arrive at mock:regular with their priorities preserved.
                from(componentName + ":queue:r1").to(componentName + ":queue:regular-1");
                from(componentName + ":queue:regular-1").to("mock:regular-1");
                from(componentName + ":queue:r2").to(componentName + ":queue:regular-2");
                from(componentName + ":queue:regular-2").to("mock:regular-2");

            }
        };
    }
}
