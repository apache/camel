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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A listed object that cannot be got (access denied, an archived object that is not restored) must not stop the
 * consumer: the other listed objects are consumed, and the poll fails only when none of the listed objects could be
 * got. The fake bucket lists its keys in order, a page of maxKeys keys at a time with the last key of a page as next
 * marker, and answers the get of a forbidden object with 403.
 */
class OBSConsumerObjectThatCannotBeGotTest extends CamelTestSupport {

    private static final String BUCKET = "bucket";

    private final Set<String> objects = new ConcurrentSkipListSet<>();
    private final Set<String> forbidden = ConcurrentHashMap.newKeySet();
    private final AtomicInteger failedPolls = new AtomicInteger();
    private final List<String> handled = new CopyOnWriteArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("obsClient", obsClient());
        context.getRegistry().bind("countPolls", new PollingConsumerPollStrategy() {
            @Override
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            @Override
            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                // noop
            }

            @Override
            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                failedPolls.incrementAndGet();
                return false;
            }
        });
        context.getRegistry().bind("recordErrors", new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handled.add(String.valueOf(exception.getMessage()));
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handled.add(message);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                handled.add(message);
            }
        });
        return context;
    }

    @Test
    void objectThatCannotBeGotDoesNotBlockTheOtherObjects() throws Exception {
        objects.add("a.txt");
        objects.add("b.txt");
        objects.add("c.txt");
        forbidden.add("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a.txt", "c.txt");

        addConsumerRoute("&deleteAfterRead=true&exceptionHandler=#recordErrors");

        mock.assertIsSatisfied();
        assertEquals(Set.of("b.txt"), Set.copyOf(objects), "a.txt and c.txt are consumed and deleted, b.txt stays");
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertTrue(handled.stream().anyMatch(m -> m.contains("b.txt")), "the skipped object is reported"));
    }

    @Test
    void objectThatCannotBeGotDoesNotBlockTheObjectsOfItsPage() throws Exception {
        objects.add("a.txt");
        objects.add("b.txt");
        objects.add("c.txt");
        objects.add("d.txt");
        forbidden.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("b.txt", "c.txt", "d.txt");

        // two objects per page: a.txt shares the first page with b.txt
        addConsumerRoute("&deleteAfterRead=true&maxMessagesPerPoll=2");

        mock.assertIsSatisfied();
        assertEquals(Set.of("a.txt"), Set.copyOf(objects), "b.txt, c.txt and d.txt are consumed and deleted");
    }

    @Test
    void pollFailsWhenNoListedObjectCanBeGot() throws Exception {
        objects.add("a.txt");
        objects.add("b.txt");
        forbidden.add("a.txt");
        forbidden.add("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        addConsumerRoute("&deleteAfterRead=true&pollStrategy=#countPolls");

        // as for a failed listing (expired credentials, no permission to get objects): the poll fails, so the error
        // handling and the backoff of the scheduled consumer apply
        await().atMost(20, TimeUnit.SECONDS).until(() -> failedPolls.get() >= 2);
        mock.assertIsSatisfied();
    }

    private void addConsumerRoute(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("hwcloud-obs:?bucketName=" + BUCKET + "&obsClient=#obsClient&initialDelay=0&delay=10" + options)
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });
    }

    private ObsClient obsClient() {
        ObsClient client = Mockito.mock(ObsClient.class);
        Mockito.when(client.listObjects(Mockito.any(ListObjectsRequest.class))).thenAnswer(i -> {
            ListObjectsRequest request = i.getArgument(0);
            String after = request.getMarker();
            int maxKeys = request.getMaxKeys() > 0 ? request.getMaxKeys() : 1000;
            List<String> remaining = objects.stream().filter(k -> after == null || k.compareTo(after) > 0).toList();
            List<String> page = remaining.stream().limit(maxKeys).toList();
            boolean truncated = remaining.size() > page.size();
            // listed objects carry no content type, so the consumer gets each of them
            List<ObsObject> listed = page.stream().map(key -> {
                ObsObject summary = new ObsObject();
                summary.setBucketName(BUCKET);
                summary.setObjectKey(key);
                return summary;
            }).toList();
            ObjectListing listing = Mockito.mock(ObjectListing.class);
            Mockito.when(listing.getObjects()).thenReturn(listed);
            Mockito.when(listing.isTruncated()).thenReturn(truncated);
            Mockito.when(listing.getNextMarker()).thenReturn(truncated ? page.get(page.size() - 1) : null);
            return listing;
        });
        Mockito.when(client.getObject(Mockito.eq(BUCKET), Mockito.anyString())).thenAnswer(i -> {
            String key = i.getArgument(1);
            if (!objects.contains(key)) {
                ObsException e = new ObsException("NoSuchKey");
                e.setResponseCode(404);
                throw e;
            }
            if (forbidden.contains(key)) {
                ObsException e = new ObsException("AccessDenied");
                e.setResponseCode(403);
                throw e;
            }
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
