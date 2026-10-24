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
package org.apache.camel.component.sjms2.batch;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.jms.ConnectionFactory;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms2.support.Jms2TestSupport;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BatchConsumerTransactedTest extends Jms2TestSupport {

    private static final String QUEUE_NAME_TEMPLATE = "sjms2.batch.consumer.%s.BatchConsumerTransactedTest";

    static final String MOCK_START = "mock:%s.start";
    static final String MOCK_FINISH = "mock:%s.complete";

    private static final String ROUTE_ID_SESSION_TX = "tx";
    private static final String ROUTE_ID_CLIENT_ACK_NO_TX = "no-tx-client-ack";
    private static final String ROUTE_ID_AUTO_ACK_NO_TX = "no-tx-auto";

    private static final String MESSAGE_TEXT = "Message %d";

    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createTCPAllProtocolsService();

    @Test
    public void testClientAcknowledgedNotTransacted() throws Exception {
        MockEndpoint mockStart = getMockEndpoint(format(MOCK_START, ROUTE_ID_CLIENT_ACK_NO_TX));
        mockStart.expectedMessageCount(2);

        MockEndpoint mockFinish = getMockEndpoint(format(MOCK_FINISH, ROUTE_ID_CLIENT_ACK_NO_TX));
        mockFinish.expectedMessageCount(1);

        for (int i = 1; i <= 5; i++) {
            template.sendBody("sjms2:queue:" + format(QUEUE_NAME_TEMPLATE, ROUTE_ID_CLIENT_ACK_NO_TX),
                    format(MESSAGE_TEXT, i));
        }

        MockEndpoint.assertIsSatisfied(context);

        List<Exchange> batch = mockFinish.getExchanges().get(0).getMessage().getBody(List.class);
        assertNotNull(batch);
        assertEquals(5, batch.size());

        int messageNumber = 1;
        for (Exchange batchExchange : batch) {
            assertEquals(
                    String.format(MESSAGE_TEXT, messageNumber++),
                    batchExchange.getMessage().getBody(String.class));
        }
    }

    @Test
    public void testSessionTransacted() throws Exception {
        MockEndpoint mockStart = getMockEndpoint(format(MOCK_START, ROUTE_ID_SESSION_TX));
        mockStart.expectedMessageCount(2);

        MockEndpoint mockFinish = getMockEndpoint(format(MOCK_FINISH, ROUTE_ID_SESSION_TX));
        mockFinish.expectedMessageCount(1);

        for (int i = 1; i <= 5; i++) {
            template.sendBody("sjms2:queue:" + format(QUEUE_NAME_TEMPLATE, ROUTE_ID_SESSION_TX),
                    format(MESSAGE_TEXT, i));
        }

        MockEndpoint.assertIsSatisfied(context);

        List<Exchange> batch = mockFinish.getExchanges().get(0).getMessage().getBody(List.class);
        assertNotNull(batch);
        assertEquals(5, batch.size());

        int messageNumber = 1;
        for (Exchange batchExchange : batch) {
            assertEquals(
                    String.format(MESSAGE_TEXT, messageNumber++),
                    batchExchange.getMessage().getBody(String.class));
        }
    }

    @Test
    public void testAutoAcknowledgedNotTransacted() throws Exception {
        MockEndpoint mockStart = getMockEndpoint(format(MOCK_START, ROUTE_ID_AUTO_ACK_NO_TX));
        mockStart.expectedMessageCount(1);

        MockEndpoint mockFinish = getMockEndpoint(format(MOCK_FINISH, ROUTE_ID_AUTO_ACK_NO_TX));
        mockFinish.expectedMessageCount(0);

        for (int i = 1; i <= 5; i++) {
            template.sendBody("sjms2:queue:" + format(QUEUE_NAME_TEMPLATE, ROUTE_ID_AUTO_ACK_NO_TX),
                    format(MESSAGE_TEXT, i));
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("sjms2:%s?batching=true&batchSize=5&batchInterval=10000&transacted=%s&acknowledgementMode=%s",
                        format(QUEUE_NAME_TEMPLATE, ROUTE_ID_AUTO_ACK_NO_TX),
                        false,
                        "AUTO_ACKNOWLEDGE")
                        .routeId(ROUTE_ID_AUTO_ACK_NO_TX)
                        .toF(MOCK_START, ROUTE_ID_AUTO_ACK_NO_TX)
                        .process(new ThrowExceptionProcessor())
                        .toF(MOCK_FINISH, ROUTE_ID_AUTO_ACK_NO_TX);

                fromF("sjms2:%s?batching=true&batchSize=5&batchInterval=10000&transacted=%s&acknowledgementMode=%s",
                        format(QUEUE_NAME_TEMPLATE, ROUTE_ID_SESSION_TX),
                        true,
                        "SESSION_TRANSACTED")
                        .routeId(ROUTE_ID_SESSION_TX)
                        .toF(MOCK_START, ROUTE_ID_SESSION_TX)
                        .process(new ThrowExceptionProcessor())
                        .toF(MOCK_FINISH, ROUTE_ID_SESSION_TX);

                fromF("sjms2:%s?batching=true&batchSize=5&batchInterval=10000&transacted=%s&acknowledgementMode=%s",
                        format(QUEUE_NAME_TEMPLATE, ROUTE_ID_CLIENT_ACK_NO_TX),
                        false,
                        "CLIENT_ACKNOWLEDGE")
                        .routeId(ROUTE_ID_CLIENT_ACK_NO_TX)
                        .toF(MOCK_START, ROUTE_ID_CLIENT_ACK_NO_TX)
                        .process(new ThrowExceptionProcessor())
                        .toF(MOCK_FINISH, ROUTE_ID_CLIENT_ACK_NO_TX);
            }
        };
    }

    protected ConnectionFactory getConnectionFactory() throws Exception {
        return getConnectionFactory(service.serviceAddress());
    }

    private static class ThrowExceptionProcessor implements Processor {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public void process(Exchange exchange) {
            int minimumBatchAttempt = 1;
            if (counter.incrementAndGet() <= minimumBatchAttempt) {
                throw new IllegalArgumentException("Forced rollback");
            }
        }
    }
}
