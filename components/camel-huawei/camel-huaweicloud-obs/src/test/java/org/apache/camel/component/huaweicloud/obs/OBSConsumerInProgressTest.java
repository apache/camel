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
package org.apache.camel.component.huaweicloud.obs;

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

import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.ListObjectsRequest;
import com.obs.services.model.ObjectListing;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The consumer keeps the objects whose exchanges are being processed: an object still in flight is not consumed again
 * by the next poll, an object is consumed again once its exchange failed or completed (without deleteAfterRead), an
 * object deleted after the listing is skipped without failing the poll, and the objects of a batch that a stopping
 * consumer does not process are consumed when the route is started again.
 */
public class OBSConsumerInProgressTest extends CamelTestSupport {

    private static final String BUCKET = "bucket";

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
        context.getRegistry().bind("obsClient", obsClient());
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
    public void objectInFlightIsNotConsumedAgain() throws Exception {
        assertNotConsumedAgain("");
    }

    @Test
    public void objectInFlightIsNotConsumedAgainWithFileName() throws Exception {
        assertNotConsumedAgain("&fileName=a.txt");
    }

    @Test
    public void failedObjectIsConsumedAgain() throws Exception {
        objects.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a.txt");

        addConsumerRoute("&deleteAfterRead=true", "seda:failOnce");

        mock.assertIsSatisfied();
        assertEquals(2, attempts.get(), "a.txt should be consumed again after its first exchange failed");
    }

    @Test
    public void completedObjectIsConsumedAgainWithoutDelete() throws Exception {
        objects.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);
        mock.allMessages().body().isEqualTo("a.txt");

        addConsumerRoute("", "mock:result");

        // without deleteAfterRead every poll reads the object again once its previous exchange is done
        mock.assertIsSatisfied();
    }

    @Test
    public void deletedObjectIsSkipped() throws Exception {
        objects.add("a.txt");
        objects.add("b.txt");
        objects.add("c.txt");
        deletedAfterListing.add("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a.txt", "c.txt");

        addConsumerRoute("&deleteAfterRead=true&pollStrategy=#countPolls", "mock:result");

        mock.assertIsSatisfied();
        assertEquals(0, failedPolls.get(), "the poll failed because b.txt was deleted after the listing");
        assertEquals(1, gets("a.txt"), "a.txt was read again because the poll that listed b.txt failed");
    }

    @Test
    public void objectsOfAStoppedBatchAreConsumedAfterRestart() throws Exception {
        objects.add("a.txt");
        objects.add("b.txt");
        objects.add("c.txt");
        AtomicBoolean first = new AtomicBoolean(true);
        MockEndpoint mock = getMockEndpoint("mock:result");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(obsUri("&deleteAfterRead=true")).routeId("obs")
                        .process(e -> {
                            if (first.compareAndSet(true, false)) {
                                // stop the route while the first exchange of the batch is processed, and wait until
                                // the consumer does not process the rest of the batch
                                OBSConsumer consumer = (OBSConsumer) context.getRoute("obs").getConsumer();
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        context.getRouteController().stopRoute("obs");
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
                .until(() -> context.getRouteController().getRouteStatus("obs") == ServiceStatus.Stopped);

        mock.reset();
        mock.expectedBodiesReceivedInAnyOrder("b.txt", "c.txt");
        context.getRouteController().startRoute("obs");

        mock.assertIsSatisfied();
    }

    private void assertNotConsumedAgain(String options) throws Exception {
        objects.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a.txt");

        addConsumerRoute("&deleteAfterRead=true&pollStrategy=#countPolls" + options, "seda:hold");

        assertTrue(inRoute.await(20, TimeUnit.SECONDS), "the object was not consumed");
        // the second poll has completed while the first exchange is still held in the seda route
        assertTrue(twoPolls.await(20, TimeUnit.SECONDS), "the consumer did not poll again");
        int getsWhileInFlight = gets("a.txt");
        release.countDown();

        assertEquals(1, getsWhileInFlight,
                "a.txt was consumed again by a later poll while its first exchange was still in flight");
        mock.assertIsSatisfied();
    }

    private String obsUri(String options) {
        return "hwcloud-obs:?bucketName=" + BUCKET + "&obsClient=#obsClient&initialDelay=0&delay=10" + options;
    }

    private void addConsumerRoute(String options, String to) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(obsUri(options))
                        .convertBodyTo(String.class)
                        .to(to);
            }
        });
    }

    private int gets(String key) {
        AtomicInteger n = gets.get(key);
        return n != null ? n.get() : 0;
    }

    private ObsClient obsClient() {
        ObsClient client = Mockito.mock(ObsClient.class);
        Mockito.when(client.listObjects(Mockito.any(ListObjectsRequest.class))).thenAnswer(i -> {
            // listed objects carry no content type, so the consumer gets each of them
            List<ObsObject> listed = objects.stream().map(key -> {
                ObsObject summary = new ObsObject();
                summary.setBucketName(BUCKET);
                summary.setObjectKey(key);
                return summary;
            }).toList();
            // deleted (as if by another consumer) right after the listing returned it
            objects.removeAll(deletedAfterListing);
            ObjectListing listing = Mockito.mock(ObjectListing.class);
            Mockito.when(listing.getObjects()).thenReturn(listed);
            return listing;
        });
        Mockito.when(client.getObject(Mockito.eq(BUCKET), Mockito.anyString())).thenAnswer(i -> {
            String key = i.getArgument(1);
            if (!objects.contains(key)) {
                ObsException e = new ObsException("NoSuchKey");
                e.setResponseCode(404);
                throw e;
            }
            gets.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
            ObsObject object = new ObsObject();
            object.setBucketName(BUCKET);
            object.setObjectKey(key);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("text/plain");
            object.setMetadata(metadata);
            object.setObjectContent(new ByteArrayInputStream(key.getBytes(StandardCharsets.UTF_8)));
            return object;
        });
        Mockito.when(client.deleteObject(Mockito.eq(BUCKET), Mockito.anyString())).thenAnswer(i -> {
            objects.remove(i.<String> getArgument(1));
            return null;
        });
        return client;
    }
}
