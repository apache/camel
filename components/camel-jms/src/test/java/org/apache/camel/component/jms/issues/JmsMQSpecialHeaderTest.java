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
package org.apache.camel.component.jms.issues;

import java.util.HashMap;
import java.util.Map;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsMQSpecialHeaderTest extends CamelTestSupport {

    @Test
    public void testUsingSpecialIBMJMSHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello IBM");
        mock.message(0).header("JMS_IBM_Character_Set").isEqualTo("ISO8859_1");

        template.sendBodyAndHeader("activemq:queue:ibm", "Hello IBM", "JMS_IBM_Character_Set", "ISO8859_1");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUsingSpecialIBMJMSHeaderAndStandardJMSHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello IBM");
        mock.message(0).header("JMS_IBM_Character_Set").isEqualTo("ISO8859_1");
        
        Map<String, Object> headers = new HashMap<>();
        headers.put("JMSPriority", 3);
        headers.put("JMS_IBM_Character_Set", "ISO8859_1");

        template.sendBodyAndHeaders("activemq:queue:ibm", "Hello IBM", headers);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:queue:ibm").to("mock:result");
            }
        };
    }

}
