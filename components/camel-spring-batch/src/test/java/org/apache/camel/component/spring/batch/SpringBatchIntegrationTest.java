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
package org.apache.camel.component.spring.batch;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringBatchIntegrationTest {

    ApplicationContext applicationContext;

    ProducerTemplate producerTemplate;

    MockEndpoint outputEndpoint;

    MockEndpoint jobExecutionEventsQueueEndpoint;

    String[] inputMessages = new String[]{"foo", "bar", "baz", null};

    @Before
    public void setUp() {
        applicationContext = new ClassPathXmlApplicationContext("classpath:org/apache/camel/component/spring/batch/springBatchtestContext.xml");
        producerTemplate = applicationContext.getBean(ProducerTemplate.class);
        outputEndpoint = applicationContext.getBean(CamelContext.class).getEndpoint("mock:output", MockEndpoint.class);
        jobExecutionEventsQueueEndpoint = applicationContext.getBean(CamelContext.class).getEndpoint("mock:jobExecutionEventsQueue", MockEndpoint.class);

        for (String message : inputMessages) {
            producerTemplate.sendBody("seda:inputQueue", message);
        }
    }

    @Test
    public void shouldEchoInBatch() throws InterruptedException {
        outputEndpoint.expectedBodiesReceived("Echo foo", "Echo bar", "Echo baz");

        producerTemplate.sendBody("direct:start", "Start batch!");

        outputEndpoint.assertIsSatisfied();
    }

    @Test
    public void shouldGenerateBatchExecutionEvents() throws InterruptedException {
        jobExecutionEventsQueueEndpoint.setExpectedMessageCount(2);

        producerTemplate.sendBody("direct:start", "Start batch!");

        jobExecutionEventsQueueEndpoint.assertIsSatisfied();
    }
}
