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
package org.apache.camel.component.alibaba.oss;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.exceptions.OperationException;
import com.aliyun.sdk.service.oss2.exceptions.ServiceException;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Request;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Result;
import com.aliyun.sdk.service.oss2.models.ObjectSummary;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The consumer keeps the objects whose exchanges are being processed: an object still in flight is not consumed again
 * by the next poll, an object is consumed again once its exchange failed or completed (without deleteAfterRead), an
 * object deleted after the listing is skipped without failing the poll, and the objects of a batch that a stopping
 * consumer does not process are consumed when the route is started again.
 */
class OSSConsumerInProgressTest extends CamelTestSupport {

    private final Set<String> objects = new ConcurrentSkipListSet<>();
    private final Set<String> deletedAfterListing = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicInteger> gets = new ConcurrentHashMap<>();
    private final AtomicInteger failedPolls = new AtomicInteger();
    private final AtomicInteger attempts = new AtomicInteger();
    private final CountDownLatch inRoute = new CountDownLatch(1);
    private final CountDownLatch twoPolls = new CountDownLatch(2);
    private final CountDownLatch release = new CountDownLatch(1);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("ossClient", ossClient());
        // counts the polls that completed (the exchanges of the poll were handed to the route) and the failed polls
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
                failedPolls.incrementAndGet();
                return false;
            }
        });
        return context;
    }

    @AfterEach
    void releaseRoute() {
        release.countDown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // seda hands the on completions (the delete) over to this route
                from("seda:hold")
                        .process(e -> {
                            inRoute.countDown();
                            release.await(20, TimeUnit.SECONDS);
                        })
                        .to("mock:result");

                from("seda:failOnce")
                        .process(e -> {
                            if (attempts.incrementAndGet() == 1) {
                                throw new IllegalStateException("first attempt fails");
                            }
                        })
                        .to("mock:result");
            }
        };
    }

    @Test
    void objectInFlightIsNotConsumedAgain() throws Exception {
        objects.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a.txt");

        addConsumerRoute("&deleteAfterRead=true&pollStrategy=#countPolls", "seda:hold");

        assertThat(inRoute.await(20, TimeUnit.SECONDS)).as("the object was not consumed").isTrue();
        // the second poll has completed while the first exchange is still held in the seda route
        assertThat(twoPolls.await(20, TimeUnit.SECONDS)).as("the consumer did not poll again").isTrue();
        int getsWhileInFlight = gets("a.txt");
        release.countDown();

        assertThat(getsWhileInFlight)
                .as("a.txt was consumed again by a later poll while its first exchange was still in flight")
                .isEqualTo(1);
        mock.assertIsSatisfied();
    }

    @Test
    void failedObjectIsConsumedAgain() throws Exception {
        objects.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a.txt");

        addConsumerRoute("&deleteAfterRead=true", "seda:failOnce");

        mock.assertIsSatisfied();
        assertThat(attempts).as("a.txt should be consumed again after its first exchange failed").hasValue(2);
    }

    @Test
    void completedObjectIsConsumedAgainWithoutDelete() throws Exception {
        objects.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);
        mock.allMessages().body().isEqualTo("a.txt");

        addConsumerRoute("", "mock:result");

        // without deleteAfterRead every poll reads the object again once its previous exchange is done
        mock.assertIsSatisfied();
    }

    @Test
    void deletedObjectIsSkipped() throws Exception {
        objects.add("a.txt");
        objects.add("b.txt");
        objects.add("c.txt");
        deletedAfterListing.add("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a.txt", "c.txt");

        addConsumerRoute("&deleteAfterRead=true&pollStrategy=#countPolls", "mock:result");

        mock.assertIsSatisfied();
        assertThat(failedPolls).as("the poll failed because b.txt was deleted after the listing").hasValue(0);
        assertThat(gets("a.txt")).as("a.txt was read again because the poll that listed b.txt failed").isEqualTo(1);
    }

    @Test
    void objectsOfAStoppedBatchAreConsumedAfterRestart() throws Exception {
        objects.add("a.txt");
        objects.add("b.txt");
        objects.add("c.txt");
        AtomicBoolean first = new AtomicBoolean(true);
        MockEndpoint mock = getMockEndpoint("mock:result");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("alibaba-oss:bucket?ossClient=#ossClient&initialDelay=0&delay=10&deleteAfterRead=true").routeId("oss")
                        .process(e -> {
                            if (first.compareAndSet(true, false)) {
                                // stop the route while the first exchange of the batch is processed, and wait until
                                // the consumer does not process the rest of the batch
                                OSSConsumer consumer = (OSSConsumer) context.getRoute("oss").getConsumer();
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        context.getRouteController().stopRoute("oss");
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

        mock.expectedBodiesReceived("a.txt");
        mock.assertIsSatisfied();
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> context.getRouteController().getRouteStatus("oss") == ServiceStatus.Stopped);

        mock.reset();
        mock.expectedBodiesReceivedInAnyOrder("b.txt", "c.txt");
        context.getRouteController().startRoute("oss");

        mock.assertIsSatisfied();
    }

    private void addConsumerRoute(String options, String to) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("alibaba-oss:bucket?ossClient=#ossClient&initialDelay=0&delay=10" + options)
                        .convertBodyTo(String.class)
                        .to(to);
            }
        });
    }

    private int gets(String key) {
        AtomicInteger n = gets.get(key);
        return n != null ? n.get() : 0;
    }

    private OSSClient ossClient() {
        OSSClient client = Mockito.mock(OSSClient.class);
        Mockito.when(client.listObjectsV2(Mockito.any(ListObjectsV2Request.class))).thenAnswer(i -> {
            List<ObjectSummary> contents = objects.stream().map(key -> {
                ObjectSummary summary = Mockito.mock(ObjectSummary.class);
                Mockito.when(summary.key()).thenReturn(key);
                return summary;
            }).toList();
            // deleted (as if by another consumer) right after the listing returned it
            objects.removeAll(deletedAfterListing);
            ListObjectsV2Result result = Mockito.mock(ListObjectsV2Result.class);
            Mockito.when(result.contents()).thenReturn(contents);
            return result;
        });
        Mockito.when(client.getObject(Mockito.any(GetObjectRequest.class))).thenAnswer(i -> {
            String key = i.<GetObjectRequest> getArgument(0).key();
            if (!objects.contains(key)) {
                throw new OperationException(
                        "GetObject", ServiceException.newBuilder().statusCode(404).build());
            }
            gets.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
            GetObjectResult result = Mockito.mock(GetObjectResult.class);
            Mockito.when(result.body()).thenReturn(new ByteArrayInputStream(key.getBytes(StandardCharsets.UTF_8)));
            return result;
        });
        Mockito.when(client.deleteObject(Mockito.any(DeleteObjectRequest.class))).thenAnswer(i -> {
            objects.remove(i.<DeleteObjectRequest> getArgument(0).key());
            return null;
        });
        return client;
    }
}
