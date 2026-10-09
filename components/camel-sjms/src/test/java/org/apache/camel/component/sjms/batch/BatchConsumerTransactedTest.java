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
import java.util.stream.IntStream;

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
import static org.apache.camel.component.sjms.batch.BatchTestHelper.getBatchBodiesAsString;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.sendMessagesWithText;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BatchConsumerTransactedTest extends JmsTestSupport {

    private static final String QUEUE_NAME_TEMPLATE = "batch.consumer.%s.BatchConsumerTransactedTest";

    private static final String ROUTE_ID_SESSION_TX = "tx";
    private static final String ROUTE_ID_CLIENT_ACK_NO_TX = "no-tx-client-ack";
    private static final String ROUTE_ID_AUTO_ACK_NO_TX = "no-tx-auto";

    private static final String MESSAGE_TEXT = "Message %d";

    @Test
    public void testSessionTransacted() throws Exception {
        MockEndpoint mockStart = getMockEndpoint(format(BATCH_ROUTEBUILDER_MOCK_START, ROUTE_ID_SESSION_TX));
        mockStart.expectedMessageCount(2);

        MockEndpoint mockFinish = getMockEndpoint(format(BATCH_ROUTEBUILDER_MOCK_FINISH, ROUTE_ID_SESSION_TX));
        mockFinish.expectedMessageCount(1);

        sendMessagesWithText(template, format("sjms:queue:" + QUEUE_NAME_TEMPLATE, ROUTE_ID_SESSION_TX),
                5, MESSAGE_TEXT);

        MockEndpoint.assertIsSatisfied(context);
        assertBatchSizesInOrder(mockFinish, 5);
        assertEquals(
                IntStream.rangeClosed(1, 5)
                        .mapToObj(i -> String.format(MESSAGE_TEXT, i))
                        .toList(),
                getBatchBodiesAsString(mockFinish.getExchanges().get(0)));
    }

    @Test
    public void testClientAcknowledgedNotTransacted() throws Exception {
        MockEndpoint mockStart = getMockEndpoint(format(BATCH_ROUTEBUILDER_MOCK_START, ROUTE_ID_CLIENT_ACK_NO_TX));
        mockStart.expectedMessageCount(2);

        MockEndpoint mockFinish = getMockEndpoint(format(BATCH_ROUTEBUILDER_MOCK_FINISH, ROUTE_ID_CLIENT_ACK_NO_TX));
        mockFinish.expectedMessageCount(1);

        sendMessagesWithText(template,
                format("sjms:queue:" + QUEUE_NAME_TEMPLATE, ROUTE_ID_CLIENT_ACK_NO_TX),
                5, MESSAGE_TEXT);

        MockEndpoint.assertIsSatisfied(context);
        assertBatchSizesInOrder(mockFinish, 5);
        assertEquals(
                IntStream.rangeClosed(1, 5)
                        .mapToObj(i -> String.format(MESSAGE_TEXT, i))
                        .toList(),
                getBatchBodiesAsString(mockFinish.getExchanges().get(0)));
    }

    @Test
    public void testAutoAcknowledgedNotTransacted() throws Exception {
        MockEndpoint mockStart = getMockEndpoint(format(BATCH_ROUTEBUILDER_MOCK_START, ROUTE_ID_AUTO_ACK_NO_TX));
        mockStart.expectedMessageCount(1);

        MockEndpoint mockFinish = getMockEndpoint(format(BATCH_ROUTEBUILDER_MOCK_FINISH, ROUTE_ID_AUTO_ACK_NO_TX));
        mockFinish.expectedMessageCount(0);

        sendMessagesWithText(template,
                format("sjms:queue:" + QUEUE_NAME_TEMPLATE, ROUTE_ID_AUTO_ACK_NO_TX), 5, MESSAGE_TEXT);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] {
                createBatchRoute(QUEUE_NAME_TEMPLATE, ROUTE_ID_SESSION_TX, 5,
                        1000,
                        true, null, 1, new ThrowExceptionProcessor()),
                createBatchRoute(QUEUE_NAME_TEMPLATE, ROUTE_ID_CLIENT_ACK_NO_TX,
                        5, 1000, false, "CLIENT_ACKNOWLEDGE", 1, new ThrowExceptionProcessor()),
                createBatchRoute(QUEUE_NAME_TEMPLATE, ROUTE_ID_AUTO_ACK_NO_TX, 5, 1000, false,
                        "AUTO_ACKNOWLEDGE", 1, new ThrowExceptionProcessor())
        };
    }

    private static class ThrowExceptionProcessor implements Processor {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public void process(org.apache.camel.Exchange exchange) {
            int minimumBatchAttempt = 1;
            if (counter.incrementAndGet() <= minimumBatchAttempt) {
                throw new IllegalArgumentException("Forced rollback");
            }
        }
    }
}
