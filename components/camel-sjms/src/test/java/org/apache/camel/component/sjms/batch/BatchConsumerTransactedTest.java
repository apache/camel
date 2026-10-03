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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.sjms.batch.BatchTestSupport.assertBatchSizesInOrder;

public class BatchConsumerTransactedTest extends JmsTestSupport {

    private static final String SJMS_DESTINATION_NAME_TEMPLATE = "queue:batch.consumer.%s.test.BatchTransactedConsumerTest";

    private static final String MOCK_START = "mock:%s.start";
    private static final String MOCK_FINISH = "mock:%s.complete";
    private static final String ROUTE_ID_SESSION_TX = "tx";
    private static final String ROUTE_ID_CLIENT_ACK_NO_TX = "no-tx-client-ack";
    private static final String ROUTE_ID_AUTO_ACK_NO_TX = "no-tx-auto";

    @Test
    public void testSessionTransacted() throws Exception {
        MockEndpoint mockStart = getMockEndpoint(String.format(MOCK_START, ROUTE_ID_SESSION_TX));
        mockStart.expectedMessageCount(2);

        MockEndpoint mockFinish = getMockEndpoint(String.format(MOCK_FINISH, ROUTE_ID_SESSION_TX));
        mockFinish.expectedMessageCount(1);

        BatchTestSupport.sendMessages(template, String.format("sjms:" + SJMS_DESTINATION_NAME_TEMPLATE, ROUTE_ID_SESSION_TX),
                5);

        MockEndpoint.assertIsSatisfied(context);
        assertBatchSizesInOrder(mockFinish, 5);
    }

    @Test
    public void testClientAcknowledgedNotTransacted() throws Exception {
        MockEndpoint mockStart = getMockEndpoint(String.format(MOCK_START, ROUTE_ID_CLIENT_ACK_NO_TX));
        mockStart.expectedMessageCount(2);

        MockEndpoint mockFinish = getMockEndpoint(String.format(MOCK_FINISH, ROUTE_ID_CLIENT_ACK_NO_TX));
        mockFinish.expectedMessageCount(1);

        BatchTestSupport.sendMessages(template,
                String.format("sjms:" + SJMS_DESTINATION_NAME_TEMPLATE, ROUTE_ID_CLIENT_ACK_NO_TX),
                5);

        MockEndpoint.assertIsSatisfied(context);
        assertBatchSizesInOrder(mockFinish, 5);
    }

    @Test
    public void testAutoAcknowledgedNotTransacted() throws Exception {
        MockEndpoint mockStart = getMockEndpoint(String.format(MOCK_START, ROUTE_ID_AUTO_ACK_NO_TX));
        mockStart.expectedMessageCount(1);

        MockEndpoint mockFinish = getMockEndpoint(String.format(MOCK_FINISH, ROUTE_ID_AUTO_ACK_NO_TX));
        mockFinish.expectedMessageCount(0);

        BatchTestSupport.sendMessages(template,
                String.format("sjms:" + SJMS_DESTINATION_NAME_TEMPLATE, ROUTE_ID_AUTO_ACK_NO_TX), 5);

        MockEndpoint.assertIsSatisfied(context);
    }

    protected RouteBuilder createRoute(
            String destinationName, String id, Boolean batching, int batchSize, Boolean transacted, String acknowledgementMode,
            int concurrentConsumers)
            throws Exception {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("batching", batching);
        params.put("batchSize", batchSize);
        params.put("transacted", transacted);
        params.put("acknowledgementMode", acknowledgementMode);
        params.put("concurrentConsumers", concurrentConsumers);

        String query = params.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));

        String fromJmsEndpoint = "sjms:" + destinationName + (query.isEmpty() ? "" : "?" + query);

        return new RouteBuilder() {
            @Override
            public void configure() {
                int minimumBatchAttempt = 1;

                from(fromJmsEndpoint)
                        .id(id)
                        .to(String.format(MOCK_START, id))
                        .process(new Processor() {
                            private final AtomicInteger counter = new AtomicInteger();

                            @Override
                            public void process(Exchange exchange) {
                                if (counter.incrementAndGet() <= minimumBatchAttempt) {
                                    log.info(
                                            "less than {} batches have been processed",
                                            minimumBatchAttempt);
                                    throw new IllegalArgumentException("Forced rollback");
                                }
                            }
                        })
                        .to(String.format(MOCK_FINISH, id));
            }
        };
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() throws Exception {
        return new RoutesBuilder[] {
                createRoute(String.format(SJMS_DESTINATION_NAME_TEMPLATE, ROUTE_ID_SESSION_TX), ROUTE_ID_SESSION_TX, true, 5,
                        true, null, 1),
                createRoute(String.format(SJMS_DESTINATION_NAME_TEMPLATE, ROUTE_ID_CLIENT_ACK_NO_TX), ROUTE_ID_CLIENT_ACK_NO_TX,
                        true,
                        5, false, "CLIENT_ACKNOWLEDGE", 1),
                createRoute(String.format(SJMS_DESTINATION_NAME_TEMPLATE, ROUTE_ID_AUTO_ACK_NO_TX), ROUTE_ID_AUTO_ACK_NO_TX,
                        true, 5, false,
                        "AUTO_ACKNOWLEDGE", 1)
        };
    }
}
