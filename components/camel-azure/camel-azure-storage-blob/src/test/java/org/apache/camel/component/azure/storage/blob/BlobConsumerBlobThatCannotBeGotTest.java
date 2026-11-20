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

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.azure.core.http.HttpResponse;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponseBase;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.specialized.BlobInputStream;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
 * A listed blob that cannot be got (a blob in the archive tier, access denied) must not stop the consumer: the other
 * listed blobs are consumed, and the poll fails only when none of the listed blobs could be got. The consumer lists the
 * whole container at every poll, so before this one such blob stopped the consumer from consuming any blob.
 */
class BlobConsumerBlobThatCannotBeGotTest extends CamelTestSupport {

    private final Set<String> blobs = new ConcurrentSkipListSet<>();
    private final Set<String> archived = ConcurrentHashMap.newKeySet();
    private final CountDownLatch failedPolls = new CountDownLatch(2);
    private final CountDownLatch skippedReported = new CountDownLatch(1);
    private final List<String> handled = new CopyOnWriteArrayList<>();
    private final CountDownLatch pollFailureObserved = new CountDownLatch(1);
    private final CountDownLatch tailFailuresReported = new CountDownLatch(2);
    private final AtomicBoolean failNextContinuationPage = new AtomicBoolean();
    private final List<BlobInputStream> openedStreams = new CopyOnWriteArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("client", serviceClient());
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
                failedPolls.countDown();
                pollFailureObserved.countDown();
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
                record(message);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                record(message);
            }

            private void record(String message) {
                handled.add(message);
                if (message.contains("b.txt")) {
                    skippedReported.countDown();
                }
                if (message.contains("c.txt") || message.contains("d.txt")) {
                    tailFailuresReported.countDown();
                }
            }
        });
        return context;
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "&maxMessagesPerPoll=2" })
    void blobThatCannotBeGotDoesNotBlockTheOtherBlobs(String maxMessagesPerPollOption) throws Exception {
        blobs.add("a.txt");
        blobs.add("b.txt");
        blobs.add("c.txt");
        archived.add("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderValuesReceivedInAnyOrder(BlobConstants.BLOB_NAME, "a.txt", "c.txt");

        addConsumerRoute("&deleteAfterRead=true" + maxMessagesPerPollOption
                         + "&exceptionHandler=#recordErrors");

        mock.assertIsSatisfied();
        assertTrue(skippedReported.await(20, TimeUnit.SECONDS), "the skipped blob is reported: " + handled);
        assertTrue(blobs.contains("b.txt"), "b.txt stays");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "&maxMessagesPerPoll=2" })
    void pollFailsWhenNoListedBlobCanBeGot(String maxMessagesPerPollOption) throws Exception {
        blobs.add("a.txt");
        blobs.add("b.txt");
        archived.add("a.txt");
        archived.add("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        addConsumerRoute("&deleteAfterRead=true" + maxMessagesPerPollOption
                         + "&pollStrategy=#countPolls");

        // as for a failed listing (expired credentials, no permission to read blobs): the poll fails, so the error
        // handling and the backoff of the scheduled consumer apply
        assertTrue(failedPolls.await(20, TimeUnit.SECONDS), "the polls fail");
        mock.assertIsSatisfied();
    }

    @Test
    void archivedBlobsOnLaterListingPageDoNotFailThePartialPoll() throws Exception {
        blobs.addAll(List.of("a.txt", "b.txt", "c.txt", "d.txt"));
        archived.addAll(Set.of("c.txt", "d.txt"));

        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMinimumMessageCount(2);
        mockEndpoint.setResultWaitTime(20_000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("azure-storage-blob://account/container?serviceClient=#client"
                     + "&initialDelay=0&delay=1000&maxMessagesPerPoll=2&maxResultsPerPage=2"
                     + "&pollStrategy=#countPolls&exceptionHandler=#recordErrors")
                        .routeId("archivedTailPageTest")
                        .autoStartup(false)
                        .to("mock:result");
            }
        });

        context.getRouteController().startRoute("archivedTailPageTest");
        try {
            assertTrue(tailFailuresReported.await(20, TimeUnit.SECONDS),
                    "the archived blobs on the second page must be reported as skipped: " + handled);
            mockEndpoint.assertIsSatisfied();

            assertEquals(2L, failedPolls.getCount(),
                    "a poll starting in the middle of the listing must not fail because its tail is archived");
        } finally {
            context.getRouteController().stopRoute("archivedTailPageTest");
        }
    }

    @Test
    void pagedListingRetainsPageRemainderAndWrapsAround() throws Exception {
        blobs.addAll(List.of("a.txt", "b.txt", "c.txt", "d.txt", "e.txt"));

        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMinimumMessageCount(6);
        mockEndpoint.setResultWaitTime(20_000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("azure-storage-blob://account/container?serviceClient=#client"
                     + "&initialDelay=0&delay=100&maxMessagesPerPoll=1&maxResultsPerPage=2")
                        .routeId("pagedCursorTest")
                        .autoStartup(false)
                        .to("mock:result");
            }
        });

        context.getRouteController().startRoute("pagedCursorTest");
        try {
            mockEndpoint.assertIsSatisfied();

            List<String> firstSixNames = mockEndpoint.getExchanges().stream()
                    .limit(6)
                    .map(exchange -> exchange.getIn().getHeader(BlobConstants.BLOB_NAME, String.class))
                    .toList();

            assertEquals(List.of("a.txt", "b.txt", "c.txt", "d.txt", "e.txt", "a.txt"),
                    firstSixNames,
                    "the consumer must retain the rest of each page, advance the continuation token, and wrap");
        } finally {
            context.getRouteController().stopRoute("pagedCursorTest");
        }
    }

    @Test
    void laterPageListingFailureReleasesPreparedExchanges() throws Exception {
        blobs.addAll(List.of("a.txt", "b.txt", "c.txt", "d.txt"));
        failNextContinuationPage.set(true);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(4);
        mockEndpoint.setResultWaitTime(30_000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("azure-storage-blob://account/container?serviceClient=#client"
                     + "&initialDelay=0&delay=5000&maxResultsPerPage=2&pollStrategy=#countPolls")
                        .routeId("laterPageFailureTest")
                        .autoStartup(false)
                        .to("mock:result");
            }
        });

        context.getRouteController().startRoute("laterPageFailureTest");
        try {
            assertTrue(pollFailureObserved.await(20, TimeUnit.SECONDS),
                    "the simulated second-page listing failure must fail the first poll");
            mockEndpoint.assertIsSatisfied();

            assertEquals(1L, failedPolls.getCount(),
                    "the next poll must restart the listing and successfully route all blobs");

            assertTrue(openedStreams.size() >= 2, "the first page must have opened two streams");
            verify(openedStreams.get(0)).close();
            verify(openedStreams.get(1)).close();
        } finally {
            context.getRouteController().stopRoute("laterPageFailureTest");
        }
    }

    private void addConsumerRoute(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("azure-storage-blob://account/container?serviceClient=#client&initialDelay=0&delay=10" + options)
                        .to("mock:result");
            }
        });
    }

    @SuppressWarnings("unchecked")
    private BlobServiceClient serviceClient() {
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(serviceClient.getBlobContainerClient(anyString())).thenReturn(containerClient);

        when(containerClient.listBlobs(
                nullable(ListBlobsOptions.class), nullable(String.class), nullable(Duration.class)))
                .thenAnswer(i -> {
                    final List<String> listing = List.copyOf(blobs);
                    final ListBlobsOptions options = i.getArgument(0);
                    final String continuationToken = i.getArgument(1);

                    if ("2".equals(continuationToken)
                            && failNextContinuationPage.compareAndSet(true, false)) {
                        throw new IllegalStateException("Simulated listing failure on the second page");
                    }

                    final int pageSize = options != null && options.getMaxResultsPerPage() != null
                            ? options.getMaxResultsPerPage() : 5000;

                    return new PagedIterable<>(
                            () -> page(listing, continuationToken, pageSize),
                            nextToken -> page(listing, nextToken, pageSize));
                });

        when(containerClient.getBlobClient(anyString())).thenAnswer(i -> blobClient(i.getArgument(0)));
        return serviceClient;
    }

    private PagedResponseBase<Void, BlobItem> page(
            List<String> listing, String continuationToken, int pageSize) {
        final int offset = continuationToken == null ? 0 : Integer.parseInt(continuationToken);
        final List<BlobItem> values = listing.stream()
                .skip(offset)
                .limit(pageSize)
                .map(name -> new BlobItem().setName(name))
                .toList();
        final int nextOffset = offset + values.size();
        final String nextToken = nextOffset < listing.size() ? Integer.toString(nextOffset) : null;

        return new PagedResponseBase<>(null, 200, null, values, nextToken, null);
    }

    private BlobClient blobClient(String name) {
        BlobClient blobClient = mock(BlobClient.class);
        when(blobClient.openInputStream(nullable(BlobRange.class), nullable(BlobRequestConditions.class))).thenAnswer(i -> {
            if (archived.contains(name)) {
                // what Azure answers to a read of a blob in the archive tier
                HttpResponse response = mock(HttpResponse.class);
                when(response.getStatusCode()).thenReturn(409);
                throw new BlobStorageException("This operation is not permitted on an archived blob.", response, null);
            }
            BlobInputStream stream = mock(BlobInputStream.class);
            when(stream.getProperties()).thenReturn(mock(BlobProperties.class));
            openedStreams.add(stream);
            return stream;
        });
        doAnswer(i -> {
            blobs.remove(name);
            return null;
        }).when(blobClient).delete();
        return blobClient;
    }
}
