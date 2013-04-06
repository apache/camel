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
 * JMSPriority being ordered using the resequencer in batch mode.
 * 
 * @version 
 */
public class JmsBatchResequencerJMSPriorityTest extends CamelTestSupport {
    
    @Test
    public void testBatchResequencerJMSPriority() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("G", "A", "B", "E", "H", "C", "D", "F");

        // must use preserveMessageQos=true to be able to specify the JMSPriority to be used
        template.sendBodyAndHeader("jms:queue:foo?preserveMessageQos=true", "A", "JMSPriority", 6);
        template.sendBodyAndHeader("jms:queue:foo?preserveMessageQos=true", "B", "JMSPriority", 6);
        template.sendBodyAndHeader("jms:queue:foo?preserveMessageQos=true", "C", "JMSPriority", 4);
        template.sendBodyAndHeader("jms:queue:foo?preserveMessageQos=true", "D", "JMSPriority", 4);
        template.sendBodyAndHeader("jms:queue:foo?preserveMessageQos=true", "E", "JMSPriority", 6);
        template.sendBodyAndHeader("jms:queue:foo?preserveMessageQos=true", "F", "JMSPriority", 4);
        template.sendBodyAndHeader("jms:queue:foo?preserveMessageQos=true", "G", "JMSPriority", 8);
        template.sendBodyAndHeader("jms:queue:foo?preserveMessageQos=true", "H", "JMSPriority", 6);

        assertMockEndpointsSatisfied();
    }
    
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("jms", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("jms:queue:foo")
                    // sort by JMSPriority by allowing duplicates (message can have same JMSPriority)
                    // and use reverse ordering so 9 is first output (most important), and 0 is last
                    // use batch mode and fire every 3th second
                    .resequence(header("JMSPriority")).batch().timeout(3000).allowDuplicates().reverse()
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

}