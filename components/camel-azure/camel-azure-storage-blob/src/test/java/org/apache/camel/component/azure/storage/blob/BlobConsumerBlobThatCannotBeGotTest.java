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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
            }
        });
        return context;
    }

    @Test
    void blobThatCannotBeGotDoesNotBlockTheOtherBlobs() throws Exception {
        blobs.add("a.txt");
        blobs.add("b.txt");
        blobs.add("c.txt");
        archived.add("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderValuesReceivedInAnyOrder(BlobConstants.BLOB_NAME, "a.txt", "c.txt");

        addConsumerRoute("&deleteAfterRead=true&exceptionHandler=#recordErrors");

        mock.assertIsSatisfied();
        assertTrue(skippedReported.await(20, TimeUnit.SECONDS), "the skipped blob is reported: " + handled);
        assertTrue(blobs.contains("b.txt"), "b.txt stays");
    }

    @Test
    void pollFailsWhenNoListedBlobCanBeGot() throws Exception {
        blobs.add("a.txt");
        blobs.add("b.txt");
        archived.add("a.txt");
        archived.add("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        addConsumerRoute("&deleteAfterRead=true&pollStrategy=#countPolls");

        // as for a failed listing (expired credentials, no permission to read blobs): the poll fails, so the error
        // handling and the backoff of the scheduled consumer apply
        assertTrue(failedPolls.await(20, TimeUnit.SECONDS), "the polls fail");
        mock.assertIsSatisfied();
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

        PagedIterable<BlobItem> listing = mock(PagedIterable.class);
        when(listing.stream()).thenAnswer(i -> blobs.stream().map(name -> new BlobItem().setName(name)).toList().stream());
        when(containerClient.listBlobs(any(), any())).thenReturn(listing);

        when(containerClient.getBlobClient(anyString())).thenAnswer(i -> blobClient(i.getArgument(0)));
        return serviceClient;
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
            return stream;
        });
        doAnswer(i -> {
            blobs.remove(name);
            return null;
        }).when(blobClient).delete();
        return blobClient;
    }
}
