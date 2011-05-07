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
package org.apache.camel.component.jms.issues;

import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Unit test to verify DLC and JMS based on user reporting
 */
public class JmsRedeliveryWithInitialRedeliveryDelayTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/issues/JmsRedeliveryWithInitialRedeliveryDelayTest-context.xml");
    }

    @Test
    public void testDLCSpringConfiguredRedeliveryPolicy() throws Exception {
        MockEndpoint dead = context.getEndpoint("mock:dead", MockEndpoint.class);
        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);

        dead.expectedBodiesReceived("Hello World");
        dead.message(0).header(Exchange.REDELIVERED).isEqualTo(true);
        dead.message(0).header(Exchange.REDELIVERY_COUNTER).isEqualTo(4);
        result.expectedMessageCount(0);

        template.sendBody("activemq:in", "Hello World");

        assertMockEndpointsSatisfied();
    }
}
