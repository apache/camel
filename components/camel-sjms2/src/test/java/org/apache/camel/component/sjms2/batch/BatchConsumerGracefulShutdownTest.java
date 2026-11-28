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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.jms.ConnectionFactory;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsConsumer;
import org.apache.camel.component.sjms2.support.Jms2TestSupport;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The graceful shutdown routes the acknowledged messages of a partial batch (AUTO_ACKNOWLEDGE) while the consumer is
 * suspended, and stops the consumer after the batch is routed.
 */
public class BatchConsumerGracefulShutdownTest extends Jms2TestSupport {

    private static final String ROUTE_ID = "auto-ack";
    private static final String QUEUE = "sjms2.batch.consumer.BatchConsumerGracefulShutdownTest";

    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createVMService();

    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicReference<ServiceStatus> consumerStatus = new AtomicReference<>();

    @Test
    public void testAutoAcknowledgePartialBatchRoutedBeforeConsumerStops() throws Exception {
        SjmsConsumer consumer = (SjmsConsumer) context.getRoute(ROUTE_ID).getConsumer();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // 3 of 5 messages: acknowledged on receipt, and buffered as a partial batch
        for (int i = 0; i < 3; i++) {
            template.sendBody("sjms2:queue:" + QUEUE, "Before!");
        }
        await().atMost(20, TimeUnit.SECONDS).until(() -> consumer.getPendingExchangesSize() == 3);

        CompletableFuture<Void> stop = CompletableFuture.runAsync(() -> {
            try {
                context.getRouteController().stopRoute(ROUTE_ID);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        });

        // the partial batch is routed while the consumer is suspended, and not by the stop of the consumer
        assertTrue(started.await(20, TimeUnit.SECONDS), "the partial batch was not routed");
        assertEquals(ServiceStatus.Suspended, consumerStatus.get());
        assertFalse(stop.isDone(), "the route stopped while its batch was routed");

        release.countDown();
        stop.get(30, TimeUnit.SECONDS);
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus(ROUTE_ID));

        mock.assertIsSatisfied();
        List<?> batch = mock.getExchanges().get(0).getMessage().getBody(List.class);
        assertEquals(3, batch.size());
    }

    @Override
    protected ConnectionFactory getConnectionFactory() throws Exception {
        return getConnectionFactory(service.serviceAddress());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // batchInterval=0: a partial batch is only dispatched when the batch size is reached, or on shutdown
                from("sjms2:queue:" + QUEUE + "?batching=true&batchSize=5&batchInterval=0"
                     + "&acknowledgementMode=AUTO_ACKNOWLEDGE")
                        .routeId(ROUTE_ID)
                        .process(this::blockFirstBatch)
                        .to("mock:result");
            }

            private void blockFirstBatch(Exchange exchange) throws Exception {
                if (started.getCount() == 0) {
                    return;
                }
                consumerStatus.set(((SjmsConsumer) getContext().getRoute(ROUTE_ID).getConsumer()).getStatus());
                started.countDown();
                if (!release.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("The batch was not released");
                }
            }
        };
    }
}
