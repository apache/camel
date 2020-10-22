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
package org.apache.camel.itest.jms;

import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.utils.extensions.JmsServiceExtension;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.support.AbstractApplicationContext;

public class AdviceWithTransactionIssueTest extends CamelSpringTestSupport {
    @RegisterExtension
    public static JmsServiceExtension jmsServiceExtension = JmsServiceExtension.createExtension();

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/itest/jms/AdviceWithTransactionIssueTest.xml");
    }

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Test
    void testAdviceWithWeaveById() throws Exception {
        AdviceWith.adviceWith(context.getRouteDefinitions().get(0), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                weaveById("mock-b*").after().to("mock:last");
            }
        });
        context.start();

        MockEndpoint mockLast = getMockEndpoint("mock:last");
        mockLast.expectedBodiesReceived("bar");
        mockLast.setExpectedMessageCount(1);

        template.sendBody("activemq:queue:start", "bar");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testAdviceWithAddLast() throws Exception {
        AdviceWith.adviceWith(context.getRouteDefinitions().get(0), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                weaveAddLast().to("mock:last");
            }
        });
        context.start();

        MockEndpoint mockLast = getMockEndpoint("mock:last");
        mockLast.expectedBodiesReceived("bar");
        mockLast.setExpectedMessageCount(1);

        template.sendBody("activemq:queue:start", "bar");

        assertMockEndpointsSatisfied();
    }

}
