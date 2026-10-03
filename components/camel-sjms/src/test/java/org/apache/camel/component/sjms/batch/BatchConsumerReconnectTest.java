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
package org.apache.camel.component.sjms.batch;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsExclusiveTestSupport;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.sjms.batch.BatchTestSupport.assertBatchSizesInOrder;

@Isolated("Seems to have problem running along with other tests")
@DisabledIfSystemProperty(named = "activemq.instance.type", matches = "remote",
                          disabledReason = "Requires control of ActiveMQ, so it can only run locally (embedded or container)")
public class BatchConsumerReconnectTest extends JmsExclusiveTestSupport {

    private static final String SJMS_FROMF_URI = "%s?batching=true&batchSize=5";
    private static final String SJMS_QUEUE_NAME = "sjms:batch.consumer.BatchConsumerReconnectTest";
    private static final String MOCK_RESULT = "mock:result";

    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createVMService();

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public ArtemisService getService() {
        return service;
    }

    @Test
    public void testBatchConsumerReconnect() throws Exception {
        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedMessageCount(2);

        BatchTestSupport.sendMessages(template, SJMS_QUEUE_NAME, 5);

        reconnect();

        BatchTestSupport.sendMessages(template, SJMS_QUEUE_NAME, 5);

        mock.assertIsSatisfied();
        assertBatchSizesInOrder(mock, 5, 5);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF(SJMS_FROMF_URI, SJMS_QUEUE_NAME).to(MOCK_RESULT);
            }
        };
    }
}
