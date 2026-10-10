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
package org.apache.camel.component.ibm.cos;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ibm.cloud.objectstorage.auth.AWSStaticCredentialsProvider;
import com.ibm.cloud.objectstorage.auth.BasicAWSCredentials;
import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3ClientBuilder;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The consumer keeps the objects whose exchanges are being processed in its in-progress repository: with fileName, an
 * object still in flight is not consumed again by the next poll, a failed get does not leave the object in progress,
 * and the objects of a batch that a stopping consumer does not process are consumed when the route is started again,
 * and their content streams are closed.
 */
class IBMCOSConsumerInProgressTest extends CamelTestSupport {

    private final CountDownLatch inRoute = new CountDownLatch(1);
    private final CountDownLatch twoPolls = new CountDownLatch(2);
    private final CountDownLatch failedPoll = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private FakeS3Server s3;
    private AmazonS3 cosClient;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        s3 = new FakeS3Server("bkt");
        s3.start();
        cosClient = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("xxx", "yyy")))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(s3.endpoint(), "us-south"))
                .withPathStyleAccessEnabled(true)
                .build();
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("cos", cosClient);
        // counts the polls that completed (the exchanges of the poll were handed to the route)
        context.getRegistry().bind("countPolls", new PollingConsumerPollStrategy() {
            @Override
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            @Override
            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                twoPolls.countDown();
            }

            @Override
            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                failedPoll.countDown();
                return false;
            }
        });
        return context;
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        if (cosClient != null) {
            cosClient.shutdown();
        }
        if (s3 != null) {
            s3.stop();
        }
    }

    @Test
    void objectInFlightIsNotConsumedAgainWithFileName() throws Exception {
        s3.put("a.txt", "hello");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ibm-cos://bkt?cosClient=#cos&delay=10&fileName=a.txt&pollStrategy=#countPolls")
                        .to("seda:hold");

                // seda hands the on completions (the delete) over to this route
                from("seda:hold")
                        .process(e -> {
                            inRoute.countDown();
                            release.await(20, TimeUnit.SECONDS);
                        })
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });

        assertTrue(inRoute.await(20, TimeUnit.SECONDS), "the object was not consumed");
        // the second poll has completed while the first exchange is still held in the seda route
        assertTrue(twoPolls.await(20, TimeUnit.SECONDS), "the consumer did not poll again");
        int gets = s3.gets("a.txt");
        release.countDown();

        assertEquals(1, gets, "a.txt was consumed again by a later poll while its first exchange was still in flight");
        mock.assertIsSatisfied();
    }

    @Test
    void objectWithFileNameIsConsumedOnceItExists() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ibm-cos://bkt?cosClient=#cos&delay=10&fileName=a.txt&pollStrategy=#countPolls")
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });

        // the get fails (NoSuchKey) while the object does not exist, which must not leave it in progress
        assertTrue(failedPoll.await(20, TimeUnit.SECONDS), "the get of the missing object did not fail");
        s3.put("a.txt", "hello");

        mock.assertIsSatisfied();
    }

    @Test
    void objectsOfAStoppedBatchAreConsumedAfterRestart() throws Exception {
        s3.put("a.txt", "a");
        s3.put("b.txt", "b");
        s3.put("c.txt", "c");
        AtomicBoolean first = new AtomicBoolean(true);
        MockEndpoint mock = getMockEndpoint("mock:result");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ibm-cos://bkt?cosClient=#cos&delay=10").routeId("cos")
                        .process(e -> {
                            if (first.compareAndSet(true, false)) {
                                // stop the route while the first exchange of the batch is processed, and wait until
                                // the consumer does not process the rest of the batch
                                IBMCOSConsumer consumer = (IBMCOSConsumer) context.getRoute("cos").getConsumer();
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        context.getRouteController().stopRoute("cos");
                                    } catch (Exception ex) {
                                        throw new RuntimeCamelException(ex);
                                    }
                                });
                                await().atMost(20, TimeUnit.SECONDS).until(() -> !consumer.isBatchAllowed());
                            }
                        })
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });

        mock.expectedBodiesReceived("a");
        mock.assertIsSatisfied();
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> context.getRouteController().getRouteStatus("cos") == ServiceStatus.Stopped);

        mock.reset();
        mock.expectedBodiesReceivedInAnyOrder("b", "c");
        context.getRouteController().startRoute("cos");

        mock.assertIsSatisfied();
    }

    @Test
    void notProcessedExchangesCloseTheirContent() throws Exception {
        IBMCOSEndpoint endpoint = context.getEndpoint("ibm-cos://bkt?cosClient=#cos", IBMCOSEndpoint.class);
        // never started, so the consumer does not process any exchange of the batch
        IBMCOSConsumer consumer = (IBMCOSConsumer) endpoint.createConsumer(exchange -> {
        });
        endpoint.getInProgressRepository().add("b.txt");
        AtomicBoolean closed = new AtomicBoolean();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(IBMCOSConstants.KEY, "b.txt");
        exchange.getIn().setBody(new ByteArrayInputStream("b".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() {
                closed.set(true);
            }
        });
        Queue<Object> batch = new LinkedList<>();
        batch.add(exchange);

        consumer.processBatch(batch);

        assertFalse(endpoint.getInProgressRepository().contains("b.txt"), "b.txt is still in progress");
        assertTrue(closed.get(), "the content stream of b.txt was not closed");
    }
}
