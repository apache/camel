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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsExclusiveTestSupport;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static java.lang.String.format;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.BATCH_ROUTEBUILDER_MOCK_FINISH;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.assertBatchSizesInOrder;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.countWorkerThreads;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.createBatchRoute;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.getBatchBodiesAsString;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.sendMessages;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.sendMessagesWithText;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BatchConsumerStopStartTest extends JmsExclusiveTestSupport {

    private static final String QUEUE_NAME_TEMPLATE = "batch.consumer.%s.BatchConsumerStopStartTest";
    private static final String ROUTE_ID_SESSION_TX = "tx";
    private static final String ROUTE_ID_CLIENT_ACK = "client-ack";
    private static final String ROUTE_ID_AUTO_ACK = "auto-ack";
    private static final String SJMS_CONNECTION_FACTORY = "countingConsumerConnectionFactory";

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
    public void testAutoAcknowledgePartialBatchDrainedOnRouteStop() throws Exception {
        final String queueName = format(QUEUE_NAME_TEMPLATE, ROUTE_ID_AUTO_ACK);
        final String endpointUri = "sjms:queue:" + queueName;

        MockEndpoint mock = getMockEndpoint(format(BATCH_ROUTEBUILDER_MOCK_FINISH, ROUTE_ID_AUTO_ACK));

        // An incomplete batch of AUTO_ACKNOWLEDGE messages should be processed on route stop
        mock.expectedMessageCount(1);

        sendMessagesWithText(template, endpointUri, 3, "Before!");

        await().atMost(Duration.ofSeconds(5))
                .until(() -> trackedConnectionFactory.messages(queueName) == 3);

        context.getRouteController().stopRoute(ROUTE_ID_AUTO_ACK);

        mock.assertIsSatisfied();
        assertEquals(List.of("Before!", "Before!", "Before!"), getBatchBodiesAsString(mock.getExchanges().get(0)));

        // AUTO_ACKNOWLEDGE messages processed in the incomplete batch should no longer be in the queue and therefore not appear in the batch after restart
        mock.reset();
        mock.expectedMessageCount(1);

        context.getRouteController().startRoute(ROUTE_ID_AUTO_ACK);
        sendMessagesWithText(template, endpointUri, 5, "After!");

        mock.assertIsSatisfied();
        assertEquals(List.of("After!", "After!", "After!", "After!", "After!"),
                getBatchBodiesAsString(mock.getExchanges().get(0)));
    }

    @Test
    public void testSessionTransactedPartialBatchNotDrainedOnRouteStop() throws Exception {
        final String queueName = format(QUEUE_NAME_TEMPLATE, ROUTE_ID_SESSION_TX);
        final String endpointUri = "sjms:queue:" + queueName;

        MockEndpoint mock = getMockEndpoint(format(BATCH_ROUTEBUILDER_MOCK_FINISH, ROUTE_ID_SESSION_TX));

        // An incomplete batch of SESSION_TRANSACTED messages should not be processed on route stop
        mock.expectedMessageCount(0);

        sendMessagesWithText(template, endpointUri, 4, "Before!");

        await().atMost(Duration.ofSeconds(5))
                .until(() -> trackedConnectionFactory.messages(queueName) == 4);

        context.getRouteController().stopRoute(ROUTE_ID_SESSION_TX);

        mock.assertIsSatisfied();

        // SESSION_TRANSACTED messages in the incomplete batch before route stop should appear in the batch after restart
        mock.reset();
        mock.setExpectedMessageCount(1);

        context.getRouteController().startRoute(ROUTE_ID_SESSION_TX);
        sendMessagesWithText(template, endpointUri, 1, "After!");

        mock.assertIsSatisfied();
        assertEquals(List.of("Before!", "Before!", "Before!", "Before!", "After!"),
                getBatchBodiesAsString(mock.getExchanges().get(0)));
    }

    @Test
    public void testClientAcknowledgePartialBatchNotDrainedOnRouteStop() throws Exception {
        final String queueName = format(QUEUE_NAME_TEMPLATE, ROUTE_ID_CLIENT_ACK);
        final String endpointUri = "sjms:queue:" + queueName;

        MockEndpoint mock = getMockEndpoint(format(BATCH_ROUTEBUILDER_MOCK_FINISH, ROUTE_ID_CLIENT_ACK));

        // An incomplete batch of CLIENT_ACKNOWLEDGE messages should not be processed on route stop
        mock.expectedMessageCount(0);

        sendMessagesWithText(template, endpointUri, 2, "Before!");

        await().atMost(Duration.ofSeconds(5))
                .until(() -> trackedConnectionFactory.messages(queueName) == 2);

        context.getRouteController().stopRoute(ROUTE_ID_CLIENT_ACK);

        mock.assertIsSatisfied();

        // CLIENT_ACKNOWLEDGE messages in the partial batch before route stop should appear in the batch after restart
        mock.reset();
        mock.setExpectedMessageCount(1);

        context.getRouteController().startRoute(ROUTE_ID_CLIENT_ACK);
        sendMessagesWithText(template, endpointUri, 3, "After!");

        mock.assertIsSatisfied();
        assertEquals(List.of("Before!", "Before!", "After!", "After!", "After!"),
                getBatchBodiesAsString(mock.getExchanges().get(0)));
    }

    @Test
    public void testRepeatedStopStartCycles() throws Exception {
        String routeId = "stop-start-cycles";
        String queueName = format(QUEUE_NAME_TEMPLATE, routeId);
        addRoute(routeId, queueName, "batchSize=5&batchInterval=60000&transacted=true&concurrentConsumers=1",
                "mock:" + routeId);

        MockEndpoint mock = getMockEndpoint("mock:" + routeId);

        for (int cycle = 0; cycle < 3; cycle++) {
            mock.reset();
            mock.expectedMessageCount(1);

            sendMessages(template, "sjms:queue:" + queueName, 5);
            mock.assertIsSatisfied();
            assertBatchSizesInOrder(mock, 5);

            context.getRouteController().stopRoute(routeId);

            // all worker threads of the stopped container must be gone before the next start
            await().atMost(Duration.ofSeconds(10)).until(() -> countWorkerThreads(queueName) == 0);

            context.getRouteController().startRoute(routeId);
        }
    }

    @Test
    public void testRestartedRouteRunsConfiguredNumberOfWorkers() throws Exception {
        String routeId = "stop-start-workers";
        String queueName = format(QUEUE_NAME_TEMPLATE, routeId);
        int consumers = 3;

        addRoute(routeId, queueName, "batchSize=5&batchInterval=60000&transacted=true&concurrentConsumers=" + consumers,
                "mock:" + routeId);

        await().atMost(10, TimeUnit.SECONDS).until(() -> countWorkerThreads(queueName) == consumers);

        context.getRouteController().stopRoute(routeId);
        await().atMost(10, TimeUnit.SECONDS).until(() -> countWorkerThreads(queueName) == 0);

        context.getRouteController().startRoute(routeId);
        await().atMost(10, TimeUnit.SECONDS).until(() -> countWorkerThreads(queueName) == consumers);
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] {
                createBatchRoute(QUEUE_NAME_TEMPLATE, ROUTE_ID_SESSION_TX, 5, 10000,
                        true, null, 1,
                        new BatchTestHelper.DoNothingProcessor(), "#" + SJMS_CONNECTION_FACTORY),
                createBatchRoute(QUEUE_NAME_TEMPLATE, ROUTE_ID_CLIENT_ACK, 5, 10000,
                        false, "CLIENT_ACKNOWLEDGE", 1,
                        new BatchTestHelper.DoNothingProcessor(), "#" + SJMS_CONNECTION_FACTORY),
                createBatchRoute(QUEUE_NAME_TEMPLATE, ROUTE_ID_AUTO_ACK, 5, 10000,
                        false, "AUTO_ACKNOWLEDGE", 1,
                        new BatchTestHelper.DoNothingProcessor(), "#" + SJMS_CONNECTION_FACTORY)
        };
    }

    private void addRoute(String routeId, String queue, String options, String mockUri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("sjms:queue:" + queue + "?batching=true&" + options)
                        .routeId(routeId)
                        .to(mockUri);
            }
        });
    }
}
