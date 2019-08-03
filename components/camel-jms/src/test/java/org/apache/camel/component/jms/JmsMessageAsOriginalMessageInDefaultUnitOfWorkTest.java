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

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JmsMessageAsOriginalMessageInDefaultUnitOfWorkTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;
    
    @Test
    public void testUseOriginalMessage() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class)
                    .useOriginalMessage()
                    .to(mockResult);
                
                from("jms:queue:foo")
                    .throwException(new Exception("forced exception for test"));
            }
        });
        context.start();

        mockResult.expectedBodiesReceived("Hello World");
        mockResult.expectedHeaderReceived("header-key", "header-value");

        template.sendBodyAndHeader("jms:queue:foo", "Hello World", "header-key", "header-value");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory)); 
        return camelContext;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}

