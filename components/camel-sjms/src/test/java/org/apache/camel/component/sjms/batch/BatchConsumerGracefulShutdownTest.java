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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsConsumer;
import org.apache.camel.component.sjms.support.JmsExclusiveTestSupport;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.sjms.batch.BatchTestHelper.assertBatchSizesInOrder;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.getBatchBodiesAsString;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.sendMessagesWithText;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The graceful shutdown waits for the batch of a batching consumer: the consumer is suspended (no more messages are
 * received), the batch being routed and the acknowledged messages of an AUTO_ACKNOWLEDGE session are routed, and only
 * then the consumer is stopped.
 */
public class BatchConsumerGracefulShutdownTest extends JmsExclusiveTestSupport {

    private static final String QUEUE_NAME_TEMPLATE = "batch.consumer.%s.BatchConsumerGracefulShutdownTest";

    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createVMService();

    @Override
    public ArtemisService getService() {
        return service;
    }

    @Test
    public void testAutoAcknowledgePartialBatchRoutedBeforeConsumerStops() throws Exception {
        String routeId = "auto-ack";
        String queue = String.format(QUEUE_NAME_TEMPLATE, routeId);
        // batchInterval=0: a partial batch is only dispatched when the batch size is reached, or on shutdown
        BlockingProcessor processor = addRoute(routeId, queue,
                "batchSize=5&batchInterval=0&acknowledgementMode=AUTO_ACKNOWLEDGE");
        SjmsConsumer consumer = (SjmsConsumer) context.getRoute(routeId).getConsumer();

        MockEndpoint mock = getMockEndpoint("mock:" + routeId);
        mock.expectedMessageCount(1);

        // 3 of 5 messages: acknowledged on receipt, and buffered as a partial batch
        sendMessagesWithText(template, "sjms:queue:" + queue, 3, "Before!");
        await().atMost(20, TimeUnit.SECONDS).until(() -> consumer.getPendingExchangesSize() == 3);

        CompletableFuture<Void> stop = stopRouteAsync(routeId);

        // the partial batch is routed while the consumer is suspended, and not by the stop of the consumer
        assertTrue(processor.started.await(20, TimeUnit.SECONDS), "the partial batch was not routed");
        assertEquals(ServiceStatus.Suspended, processor.consumerStatus.get());
        assertEquals(3, consumer.getPendingExchangesSize());
        assertFalse(stop.isDone(), "the route stopped while its batch was routed");

        processor.release.countDown();
        stop.get(30, TimeUnit.SECONDS);
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus(routeId));

        mock.assertIsSatisfied();
        assertEquals(List.of("Before!", "Before!", "Before!"), getBatchBodiesAsString(mock.getExchanges().get(0)));
    }

    @Test
    public void testTransactedBatchCompletesBeforeConsumerStops() throws Exception {
        String routeId = "tx";
        String queue = String.format(QUEUE_NAME_TEMPLATE, routeId);
        BlockingProcessor processor = addRoute(routeId, queue, "batchSize=3&batchInterval=0&transacted=true");
        SjmsConsumer consumer = (SjmsConsumer) context.getRoute(routeId).getConsumer();

        MockEndpoint mock = getMockEndpoint("mock:" + routeId);
        mock.expectedMessageCount(1);

        sendMessagesWithText(template, "sjms:queue:" + queue, 3, "Before!");
        assertTrue(processor.started.await(20, TimeUnit.SECONDS), "the batch was not routed");

        CompletableFuture<Void> stop = stopRouteAsync(routeId);

        // the graceful shutdown waits for the batch being routed
        await().atMost(20, TimeUnit.SECONDS).until(consumer::isSuspended);
        assertEquals(3, consumer.getPendingExchangesSize());
        assertFalse(stop.isDone(), "the route stopped while its batch was routed");

        processor.release.countDown();
        stop.get(30, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
        assertEquals(List.of("Before!", "Before!", "Before!"), getBatchBodiesAsString(mock.getExchanges().get(0)));

        // the batch was committed before the session was closed, so it is not redelivered
        mock.reset();
        mock.expectedMessageCount(1);
        context.getRouteController().startRoute(routeId);
        sendMessagesWithText(template, "sjms:queue:" + queue, 3, "After!");

        mock.assertIsSatisfied();
        assertBatchSizesInOrder(mock, 3);
        assertEquals(List.of("After!", "After!", "After!"), getBatchBodiesAsString(mock.getExchanges().get(0)));
    }

    private CompletableFuture<Void> stopRouteAsync(String routeId) {
        return CompletableFuture.runAsync(() -> {
            try {
                context.getRouteController().stopRoute(routeId);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        });
    }

    private BlockingProcessor addRoute(String routeId, String queue, String options) throws Exception {
        BlockingProcessor processor = new BlockingProcessor(routeId);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("sjms:queue:" + queue + "?batching=true&" + options)
                        .routeId(routeId)
                        .process(processor)
                        .to("mock:" + routeId);
            }
        });
        return processor;
    }

    /**
     * Blocks the first batch until it is released, and records the status of the consumer when the batch arrives.
     */
    private class BlockingProcessor implements Processor {

        private final String routeId;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicReference<ServiceStatus> consumerStatus = new AtomicReference<>();

        BlockingProcessor(String routeId) {
            this.routeId = routeId;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            if (started.getCount() == 0) {
                return;
            }
            consumerStatus.set(((SjmsConsumer) context.getRoute(routeId).getConsumer()).getStatus());
            started.countDown();
            if (!release.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The batch was not released");
            }
        }
    }
}
