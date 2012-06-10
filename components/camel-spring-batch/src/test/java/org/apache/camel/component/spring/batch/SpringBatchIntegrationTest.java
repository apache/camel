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

import static org.junit.Assert.assertTrue;

public class SpringBatchIntegrationTest {

    ApplicationContext applicationContext;

    ProducerTemplate producerTemplate;

    MockEndpoint outputEndpoint;

    MockEndpoint jobExecutionEventsQueueEndpoint;

    String[] inputMessages;

    @Before
    public void setUp() {
        applicationContext = new ClassPathXmlApplicationContext("classpath:org/apache/camel/component/spring/batch/springBatchtestContext.xml");
        producerTemplate = applicationContext.getBean(ProducerTemplate.class);
        outputEndpoint = applicationContext.getBean(CamelContext.class).getEndpoint("mock:output", MockEndpoint.class);
        jobExecutionEventsQueueEndpoint = applicationContext.getBean(CamelContext.class).getEndpoint("mock:jobExecutionEventsQueue", MockEndpoint.class);
        inputMessages = new String[]{"foo", "bar", "baz"};

        for (String message : inputMessages) {
            producerTemplate.sendBody("seda:inputQueue", message);
        }
        producerTemplate.sendBody("seda:inputQueue", null);

    }

    @Test
    public void shouldEchoInBatch() throws InterruptedException {
        // When
        producerTemplate.sendBody("direct:start", "Start batch!");

        // Then
        outputEndpoint.setExpectedMessageCount(inputMessages.length);
        outputEndpoint.assertIsSatisfied();
        assertTrue(outputEndpoint.getExchanges().get(0).getIn().getBody(String.class).startsWith("Echo "));
    }

    @Test
    public void shouldGenerateBatchExecutionEvents() throws InterruptedException {
        // When
        producerTemplate.sendBody("direct:start", "Start batch!");

        // Then
        jobExecutionEventsQueueEndpoint.setExpectedMessageCount(2);
        jobExecutionEventsQueueEndpoint.assertIsSatisfied();
    }

}
