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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static java.lang.String.format;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.BATCH_ROUTEBUILDER_MOCK_FINISH;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.BATCH_ROUTEBUILDER_MOCK_START;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.assertBatchSizesInOrder;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.createBatchRoute;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.sendMessages;

public class BatchConsumerRollbackOnlyTest extends JmsTestSupport {

    private static final String QUEUE_NAME_TEMPLATE = "batch.consumer.%s.BatchConsumerRollbackOnlyTest";

    private static final String ROUTE_ID_SESSION_TX = "tx";
    private static final String ROUTE_ID_CLIENT_ACK_NO_TX = "no-tx-client-ack";

    @Test
    public void testSessionTransacted() throws Exception {
        MockEndpoint mockStart = getMockEndpoint(format(BATCH_ROUTEBUILDER_MOCK_START, ROUTE_ID_SESSION_TX));
        mockStart.expectedMessageCount(2);

        MockEndpoint mockFinish = getMockEndpoint(format(BATCH_ROUTEBUILDER_MOCK_FINISH, ROUTE_ID_SESSION_TX));
        mockFinish.expectedMessageCount(1);

        sendMessages(template, format("sjms:queue:" + QUEUE_NAME_TEMPLATE, ROUTE_ID_SESSION_TX),
                5);

        MockEndpoint.assertIsSatisfied(context);
        assertBatchSizesInOrder(mockFinish, 5);
    }

    @Test
    public void testClientAcknowledgedNotTransacted() throws Exception {
        MockEndpoint mockStart = getMockEndpoint(format(BATCH_ROUTEBUILDER_MOCK_START, ROUTE_ID_CLIENT_ACK_NO_TX));
        mockStart.expectedMessageCount(2);

        MockEndpoint mockFinish = getMockEndpoint(format(BATCH_ROUTEBUILDER_MOCK_FINISH, ROUTE_ID_CLIENT_ACK_NO_TX));
        mockFinish.expectedMessageCount(1);

        sendMessages(template,
                format("sjms:queue:" + QUEUE_NAME_TEMPLATE, ROUTE_ID_CLIENT_ACK_NO_TX),
                5);

        MockEndpoint.assertIsSatisfied(context);
        assertBatchSizesInOrder(mockFinish, 5);
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] {
                createBatchRoute(QUEUE_NAME_TEMPLATE, ROUTE_ID_SESSION_TX, 5,
                        1000,
                        true, null, 1, new MarkRollBackProcessor()),
                createBatchRoute(QUEUE_NAME_TEMPLATE, ROUTE_ID_CLIENT_ACK_NO_TX,
                        5, 1000, false, "CLIENT_ACKNOWLEDGE", 1, new MarkRollBackProcessor())
        };
    }

    private static class MarkRollBackProcessor implements Processor {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public void process(org.apache.camel.Exchange exchange) {
            int minimumBatchAttempt = 1;
            if (counter.incrementAndGet() <= minimumBatchAttempt) {
                exchange.setRollbackOnly(true);
            }
        }
    }
}
