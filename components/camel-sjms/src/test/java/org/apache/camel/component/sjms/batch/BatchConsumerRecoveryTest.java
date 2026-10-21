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

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsExclusiveTestSupport;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.sjms.batch.BatchTestHelper.getBatchBodiesAsString;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.getBatchExchanges;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.sendMessagesWithText;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.triggerConnectionFailure;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BatchConsumerRecoveryTest extends JmsExclusiveTestSupport {

    private static final String SJMS_FROMF_URI
            = "%s?batching=true&batchSize=5&transacted=true&connectionFactory=#%s";
    private static final String SJMS_QUEUE_NAME
            = "sjms:queue:batch.consumer.queue.BatchConsumerRecoveryTest";
    private static final String MOCK_RESULT = "mock:result";
    private static final String SJMS_ROUTE_ID = "BatchConsumerRecoveryTest";
    private static final String SJMS_CONNECTION_FACTORY = "countingConnectionFactory";

    private BatchTestHelper.TrackedConnectionFactory trackedConnectionFactory;

    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createVMService();

    @Override
    public ArtemisService getService() {
        return service;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        trackedConnectionFactory = new BatchTestHelper.TrackedConnectionFactory(connectionFactory);
        camelContext.getRegistry().bind(SJMS_CONNECTION_FACTORY, trackedConnectionFactory.proxy());

        return camelContext;
    }

    @Test
    public void testBatchConsumerRecovery() throws Exception {
        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedMessageCount(0);

        sendMessagesWithText(template, SJMS_QUEUE_NAME, 2, "Before!");

        int connectionsBefore = trackedConnectionFactory.getCreateCount();
        triggerConnectionFailure(context, SJMS_ROUTE_ID);
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> trackedConnectionFactory.getCreateCount() > connectionsBefore);

        mock.assertIsSatisfied();
        mock.reset();

        // we want to test with 1 complete (incl. the messages prior to reconnect) and 1 incomplete batch
        mock.expectedMessageCount(2);

        sendMessagesWithText(template, SJMS_QUEUE_NAME, 7, "After!");

        mock.assertIsSatisfied();
        // fist batch
        assertEquals(5, getBatchExchanges(mock.getExchanges().get(0)).size());
        assertEquals(List.of("Before!", "Before!", "After!", "After!", "After!"),
                getBatchBodiesAsString(mock.getExchanges().get(0)));
        // second batch
        assertEquals(4, getBatchExchanges(mock.getExchanges().get(1)).size());
        assertEquals(List.of("After!", "After!", "After!", "After!"), getBatchBodiesAsString(mock.getExchanges().get(1)));
        assertEquals(connectionsBefore + 1, trackedConnectionFactory.getCreateCount());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                fromF(SJMS_FROMF_URI, SJMS_QUEUE_NAME, SJMS_CONNECTION_FACTORY)
                        .routeId(SJMS_ROUTE_ID)
                        .to(MOCK_RESULT);
            }
        };
    }
}
