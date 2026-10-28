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
package org.apache.camel.component.azure.storage.blob;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.azure.core.http.HttpResponse;
import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.specialized.BlobInputStream;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The consumer keeps the blobs whose exchanges are being processed: a blob still in flight is not consumed again by the
 * next poll, a blob is consumed again once its exchange failed or completed (without deleteAfterRead), a blob deleted
 * after the listing is skipped without failing the poll, and the blobs of a batch that a stopping consumer does not
 * process are released and their streams closed.
 */
class BlobConsumerInProgressTest extends CamelTestSupport {

    private final Set<String> blobs = new ConcurrentSkipListSet<>();
    private final Set<String> deletedAfterListing = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicInteger> reads = new ConcurrentHashMap<>();
    private final List<BlobInputStream> streams = new CopyOnWriteArrayList<>();
    private final AtomicInteger failedPolls = new AtomicInteger();
    private final AtomicInteger attempts = new AtomicInteger();
    private final CountDownLatch inRoute = new CountDownLatch(1);
    private final CountDownLatch twoPolls = new CountDownLatch(2);
    private final CountDownLatch release = new CountDownLatch(1);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("client", serviceClient());
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
    void blobInFlightIsNotConsumedAgain() throws Exception {
        assertNotConsumedAgain("");
    }

    @Test
    void blobInFlightIsNotConsumedAgainWithBlobName() throws Exception {
        assertNotConsumedAgain("&blobName=a.txt");
    }

    @Test
    void failedBlobIsConsumedAgain() throws Exception {
        blobs.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderReceived(BlobConstants.BLOB_NAME, "a.txt");

        addConsumerRoute("&deleteAfterRead=true", "seda:failOnce");

        mock.assertIsSatisfied();
        assertEquals(2, attempts.get(), "a.txt should be consumed again after its first exchange failed");
    }

    @Test
    void completedBlobIsConsumedAgainWithoutDelete() throws Exception {
        blobs.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);
        mock.allMessages().header(BlobConstants.BLOB_NAME).isEqualTo("a.txt");

        addConsumerRoute("", "mock:result");

        // without deleteAfterRead every poll reads the blob again once its previous exchange is done
        mock.assertIsSatisfied();
    }

    @Test
    void deletedBlobIsSkipped() throws Exception {
        blobs.add("a.txt");
        blobs.add("b.txt");
        blobs.add("c.txt");
        deletedAfterListing.add("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderValuesReceivedInAnyOrder(BlobConstants.BLOB_NAME, "a.txt", "c.txt");

        addConsumerRoute("&deleteAfterRead=true&pollStrategy=#countPolls", "mock:result");

        mock.assertIsSatisfied();
        // the poll that listed b.txt is not dropped, so the blobs read before b.txt are not read again by a later poll
        assertEquals(1, reads("a.txt"), "a.txt was read again because b.txt was deleted after the listing");
        assertEquals(1, reads("c.txt"));
        assertEquals(0, failedPolls.get(), "the poll failed because b.txt was deleted after the listing");
    }

    @Test
    void notProcessedBlobIsReleased() throws Exception {
        blobs.add("a.txt");
        BlobEndpoint endpoint = context.getEndpoint(
                "azure-storage-blob://account/container?serviceClient=#client&deleteAfterRead=true", BlobEndpoint.class);
        // never started, so the consumer does not process any exchange of the batch
        BlobConsumer consumer = (BlobConsumer) endpoint.createConsumer(exchange -> {
            throw new IllegalStateException("no exchange should be processed");
        });

        consumer.poll();
        consumer.poll();

        assertEquals(2, reads("a.txt"), "a.txt stayed in progress although its exchange was not processed");
        assertEquals(2, streams.size());
        for (BlobInputStream stream : streams) {
            verify(stream).close();
        }
    }

    private void assertNotConsumedAgain(String options) throws Exception {
        blobs.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderReceived(BlobConstants.BLOB_NAME, "a.txt");

        addConsumerRoute("&deleteAfterRead=true&pollStrategy=#countPolls" + options, "seda:hold");

        assertTrue(inRoute.await(20, TimeUnit.SECONDS), "the blob was not consumed");
        // the second poll has completed while the first exchange is still held in the seda route
        assertTrue(twoPolls.await(20, TimeUnit.SECONDS), "the consumer did not poll again");
        int readsWhileInFlight = reads("a.txt");
        release.countDown();

        assertEquals(1, readsWhileInFlight,
                "a.txt was consumed again by a later poll while its first exchange was still in flight");
        mock.assertIsSatisfied();
    }

    private void addConsumerRoute(String options, String to) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("azure-storage-blob://account/container?serviceClient=#client&initialDelay=0&delay=10" + options)
                        .to(to);
            }
        });
    }

    private int reads(String name) {
        AtomicInteger n = reads.get(name);
        return n != null ? n.get() : 0;
    }

    @SuppressWarnings("unchecked")
    private BlobServiceClient serviceClient() {
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(serviceClient.getBlobContainerClient(anyString())).thenReturn(containerClient);

        PagedIterable<BlobItem> listing = mock(PagedIterable.class);
        when(listing.stream()).thenAnswer(i -> {
            List<BlobItem> items = blobs.stream().map(name -> new BlobItem().setName(name)).toList();
            // deleted (as if by another consumer) right after the listing returned it
            blobs.removeAll(deletedAfterListing);
            return items.stream();
        });
        when(containerClient.listBlobs(any(), any())).thenReturn(listing);

        when(containerClient.getBlobClient(anyString())).thenAnswer(i -> blobClient(i.getArgument(0)));
        return serviceClient;
    }

    private BlobClient blobClient(String name) {
        BlobClient blobClient = mock(BlobClient.class);
        when(blobClient.openInputStream(nullable(BlobRange.class), nullable(BlobRequestConditions.class))).thenAnswer(i -> {
            if (!blobs.contains(name)) {
                HttpResponse response = mock(HttpResponse.class);
                when(response.getStatusCode()).thenReturn(404);
                throw new BlobStorageException("The specified blob does not exist.", response, null);
            }
            reads.computeIfAbsent(name, k -> new AtomicInteger()).incrementAndGet();
            BlobInputStream stream = mock(BlobInputStream.class);
            when(stream.getProperties()).thenReturn(mock(BlobProperties.class));
            streams.add(stream);
            return stream;
        });
        doAnswer(i -> {
            blobs.remove(name);
            return null;
        }).when(blobClient).delete();
        return blobClient;
    }
}
