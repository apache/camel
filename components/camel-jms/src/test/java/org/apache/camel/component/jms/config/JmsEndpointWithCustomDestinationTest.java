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
package org.apache.camel.component.jms.config;

import org.apache.activemq.command.ActiveMQQueue;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class JmsEndpointWithCustomDestinationTest extends CamelSpringTestSupport {

    private Object expectedBody = "<hello>world!</hello>";

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/config/JmsEndpointWithCustomDestinationTest-context.xml");
    }

    @Test
    public void testMessageSentToCustomEndpoint() throws Exception {
        ActiveMQQueue jmsQueue = context.getRegistry().lookupByNameAndType("jmsQueue", ActiveMQQueue.class);
        assertNotNull("jmsQueue", jmsQueue);
        assertEquals("jmsqueue.getPhysicalName()", "Test.Camel.CustomEndpoint", jmsQueue.getPhysicalName());

        getMockEndpoint("mock:result").expectedBodiesReceived(expectedBody);

        template.sendBody("direct:start", expectedBody);

        assertMockEndpointsSatisfied();
    }

}
