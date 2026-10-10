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

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.support.JmsExclusiveTestSupport;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.BATCH_ROUTEBUILDER_MOCK_FINISH;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.createBatchRoute;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.getBatchBodiesAsString;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.getBatchExchanges;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.sendMessagesWithText;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.triggerConnectionFailure;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BatchConsumerReconnectTest extends JmsExclusiveTestSupport {

    private static final String SJMS_QUEUE_NAME_TEMPLATE = "batch.consumer.%s.BatchConsumerReconnectTest";
    private static final String ROUTE_ID_SESSION_TX = "tx";
    private static final String ROUTE_ID_CLIENT_ACK_NO_TX = "no-tx-client-ack";
    private static final String ROUTE_ID_AUTO_ACK_NO_TX = "no-tx-auto";

    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createVMService();

    private BatchTestHelper.CountingConnectionFactory countingFactory;

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public ArtemisService getService() {
        return service;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        addSjmsComponent = false;
        CamelContext camelContext = super.createCamelContext();

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(service.serviceAddress());
        connectionFactory.setReconnectAttempts(0);

        countingFactory = new BatchTestHelper.CountingConnectionFactory(connectionFactory);

        SjmsComponent sjms = new SjmsComponent();
        sjms.setConnectionFactory(countingFactory);
        camelContext.addComponent("sjms", sjms);

        return camelContext;
    }

    @Test
    public void testReconnectAutoAcknowledgedNotTransacted() throws Exception {
        final String queueName = format(SJMS_QUEUE_NAME_TEMPLATE, ROUTE_ID_AUTO_ACK_NO_TX);
        final String endpointUri = "sjms:queue:" + queueName;

        MockEndpoint mock = getMockEndpoint(format(BATCH_ROUTEBUILDER_MOCK_FINISH, ROUTE_ID_AUTO_ACK_NO_TX));
        mock.expectedMessageCount(1);

        sendMessagesWithText(template, endpointUri, 2, "Before!");

        int connectionsBefore = countingFactory.getCreateCount();
        triggerConnectionFailure(context, ROUTE_ID_AUTO_ACK_NO_TX);
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> countingFactory.getCreateCount() > connectionsBefore);

        mock.assertIsSatisfied();
        assertEquals(2, getBatchExchanges(mock.getExchanges().get(0)).size());
        assertEquals(List.of("Before!", "Before!"), getBatchBodiesAsString(mock.getExchanges().get(0)));

        mock.reset();

        // test with an incomplete batch - to check timing
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(15000);
        sendMessagesWithText(template, endpointUri, 3, "After!");

        mock.assertIsSatisfied();
        assertEquals(3, getBatchExchanges(mock.getExchanges().get(0)).size());
        assertEquals(List.of("After!", "After!", "After!"), getBatchBodiesAsString(mock.getExchanges().get(0)));
    }

    @Test
    public void testSessionTransacted() throws Exception {
        final String queueName = format(SJMS_QUEUE_NAME_TEMPLATE, ROUTE_ID_SESSION_TX);
        final String endpointUri = "sjms:queue:" + queueName;

        MockEndpoint mock = getMockEndpoint(format(BATCH_ROUTEBUILDER_MOCK_FINISH, ROUTE_ID_SESSION_TX));
        mock.expectedMessageCount(0);

        sendMessagesWithText(template, endpointUri, 2, "Before!");

        int connectionsBefore = countingFactory.getCreateCount();
        triggerConnectionFailure(context, ROUTE_ID_AUTO_ACK_NO_TX);
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> countingFactory.getCreateCount() > connectionsBefore);

        mock.assertIsSatisfied();
        mock.reset();

        // we want to test with 1 complete (incl. the messages prior to reconnect) and 1 incomplete batch
        mock.expectedMessageCount(2);
        mock.setResultWaitTime(15000);
        sendMessagesWithText(template, endpointUri, 7, "After!");

        mock.assertIsSatisfied();
        // fist batch
        assertEquals(5, getBatchExchanges(mock.getExchanges().get(0)).size());
        assertEquals(List.of("Before!", "Before!", "After!", "After!", "After!"),
                getBatchBodiesAsString(mock.getExchanges().get(0)));
        // second batch
        assertEquals(4, getBatchExchanges(mock.getExchanges().get(1)).size());
        assertEquals(List.of("After!", "After!", "After!", "After!"), getBatchBodiesAsString(mock.getExchanges().get(1)));
    }

    @Test
    public void testClientAcknowledge() throws Exception {
        final String queueName = format(SJMS_QUEUE_NAME_TEMPLATE, ROUTE_ID_CLIENT_ACK_NO_TX);
        final String endpointUri = "sjms:queue:" + queueName;

        MockEndpoint mock = getMockEndpoint(format(BATCH_ROUTEBUILDER_MOCK_FINISH, ROUTE_ID_CLIENT_ACK_NO_TX));
        mock.expectedMessageCount(0);

        sendMessagesWithText(template, endpointUri, 2, "Before!");

        int connectionsBefore = countingFactory.getCreateCount();
        triggerConnectionFailure(context, ROUTE_ID_AUTO_ACK_NO_TX);
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> countingFactory.getCreateCount() > connectionsBefore);

        mock.assertIsSatisfied();
        mock.reset();

        mock.expectedMessageCount(1);
        mock.setResultWaitTime(15000);
        sendMessagesWithText(template, endpointUri, 3, "After!");

        mock.assertIsSatisfied();
        assertEquals(5, getBatchExchanges(mock.getExchanges().get(0)).size());
        assertEquals(List.of("Before!", "Before!", "After!", "After!", "After!"),
                getBatchBodiesAsString(mock.getExchanges().get(0)));
    }

    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] {
                createBatchRoute(SJMS_QUEUE_NAME_TEMPLATE, ROUTE_ID_SESSION_TX, 5,
                        10000,
                        true, null, 1, new BatchTestHelper.DoNothingProcessor()),
                createBatchRoute(SJMS_QUEUE_NAME_TEMPLATE, ROUTE_ID_CLIENT_ACK_NO_TX,
                        5, 10000, false, "CLIENT_ACKNOWLEDGE", 1, new BatchTestHelper.DoNothingProcessor()),
                createBatchRoute(SJMS_QUEUE_NAME_TEMPLATE, ROUTE_ID_AUTO_ACK_NO_TX, 5, 10000, false,
                        "AUTO_ACKNOWLEDGE", 1, new BatchTestHelper.DoNothingProcessor())
        };
    }
}
