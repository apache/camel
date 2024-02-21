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
package org.apache.camel.component.spring.batch;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

@Disabled("Requires a running database")
public class SpringBatchIT extends CamelSpringTestSupport {
    @EndpointInject("mock:header")
    MockEndpoint headerEndpoint;

    @EndpointInject("mock:output")
    MockEndpoint outputEndpoint;

    @EndpointInject("mock:jobExecutionEventsQueue")
    MockEndpoint jobExecutionEventsQueueEndpoint;

    String[] inputMessages = new String[] { "foo", "bar", "baz", null };

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        for (String message : inputMessages) {
            template.sendBody("seda:inputQueue", message);
        }
    }

    @Test
    public void shouldEchoInBatch() throws InterruptedException {
        outputEndpoint.expectedBodiesReceived("Echo foo", "Echo bar", "Echo baz");

        template.sendBody("direct:start", "Start batch!");

        outputEndpoint.assertIsSatisfied();
    }

    @Test
    public void shouldGenerateBatchExecutionEvents() throws InterruptedException {
        jobExecutionEventsQueueEndpoint.setExpectedMessageCount(2);

        template.sendBody("direct:start", "Start batch!");

        jobExecutionEventsQueueEndpoint.assertIsSatisfied();
    }

    @Test
    public void testMessageHeader() throws Exception {
        headerEndpoint.expectedHeaderReceived("header", 1);

        template.sendBodyAndHeader("direct:header", null, "header", "1");

        headerEndpoint.assertIsSatisfied();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/spring/batch/springBatchtestContext.xml");
    }
}
