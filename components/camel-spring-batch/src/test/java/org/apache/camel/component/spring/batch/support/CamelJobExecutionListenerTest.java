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
package org.apache.camel.component.spring.batch.support;

import org.apache.camel.Exchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.batch.core.JobExecution;

@RunWith(MockitoJUnitRunner.class)
public class CamelJobExecutionListenerTest extends CamelTestSupport {

    // Fixtures
    @Mock
    JobExecution jobExecution;

    CamelJobExecutionListener jobExecutionListener;

    // Camel fixtures
    @Override
    protected void doPostSetup() throws Exception {
        jobExecutionListener = new CamelJobExecutionListener(template(), "seda:eventQueue");
    }

    // Tests
    @Test
    public void shouldSendBeforeJobEvent() throws Exception {
        // When
        jobExecutionListener.beforeJob(jobExecution);

        // Then
        assertEquals(jobExecution, consumer().receiveBody("seda:eventQueue"));
    }

    @Test
    public void shouldSetBeforeJobEventHeader() throws Exception {
        // When
        jobExecutionListener.beforeJob(jobExecution);

        // Then
        Exchange beforeJobEvent = consumer().receive("seda:eventQueue");
        assertEquals(CamelJobExecutionListener.EventType.BEFORE.name(), beforeJobEvent.getIn().getHeader(CamelJobExecutionListener.EventType.HEADER_KEY));
    }

    @Test
    public void shouldSendAfterJobEvent() throws Exception {
        // When
        jobExecutionListener.afterJob(jobExecution);

        // Then
        assertEquals(jobExecution, consumer().receiveBody("seda:eventQueue"));
    }

    @Test
    public void shouldSetAfterJobEventHeader() throws Exception {
        // When
        jobExecutionListener.afterJob(jobExecution);

        // Then
        Exchange beforeJobEvent = consumer().receive("seda:eventQueue");
        assertEquals(CamelJobExecutionListener.EventType.AFTER.name(), beforeJobEvent.getIn().getHeader(CamelJobExecutionListener.EventType.HEADER_KEY));
    }

}
