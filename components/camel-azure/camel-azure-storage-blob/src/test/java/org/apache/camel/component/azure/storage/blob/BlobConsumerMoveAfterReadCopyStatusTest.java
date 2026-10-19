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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponseBase;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobCopyInfo;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.specialized.BlobInputStream;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * With moveAfterRead the consumer copies the blob to the destination container with
 * {@code beginCopy(...).waitForCompletion(timeout)} and then deletes the source blob. A copy is asynchronous on the
 * service and the poller returns normally when the copy ended as failed, aborted, or when its status could not be read:
 * the source blob must then stay, else the blob is lost (the destination of a failed or aborted copy has no content).
 */
class BlobConsumerMoveAfterReadCopyStatusTest extends CamelTestSupport {

    private final AtomicInteger handled = new AtomicInteger();
    private final CountDownLatch copied = new CountDownLatch(1);
    private final CountDownLatch errorHandled = new CountDownLatch(1);

    private BlobClient source;

    BlobConsumerMoveAfterReadCopyStatusTest() {
        testConfiguration().withUseRouteBuilder(false);
    }

    @Test
    void sourceBlobIsKeptWhenTheCopyFailed() throws Exception {
        assertSourceKept(LongRunningOperationStatus.FAILED);
    }

    @Test
    void sourceBlobIsKeptWhenTheCopyWasAborted() throws Exception {
        assertSourceKept(LongRunningOperationStatus.USER_CANCELLED);
    }

    @Test
    void sourceBlobIsKeptWhenTheCopyStatusCouldNotBeRead() throws Exception {
        // the status that BlobClientBase.beginCopy reports when reading the copy status of a pending copy failed
        assertSourceKept(LongRunningOperationStatus.fromString("POLLING_FAILED", true));
    }

    @Test
    void sourceBlobIsDeletedWhenTheCopySucceeded() throws Exception {
        startRoute(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED);

        assertTrue(copied.await(10, TimeUnit.SECONDS), "the blob was not copied");
        context.getRouteController().stopRoute("blob");

        verify(source).delete();
        assertEquals(0, handled.get());
    }

    private void assertSourceKept(LongRunningOperationStatus status) throws Exception {
        startRoute(status);

        assertTrue(copied.await(10, TimeUnit.SECONDS), "the blob was not copied");
        // a graceful stop waits for the exchange and its on completion (the move)
        context.getRouteController().stopRoute("blob");

        verify(source, never()).delete();
        assertTrue(errorHandled.await(10, TimeUnit.SECONDS), "the copy that did not succeed was not reported");
    }

    private void startRoute(LongRunningOperationStatus copyStatus) throws Exception {
        context.getRegistry().bind("client", serviceClient(copyStatus));
        context.getRegistry().bind("handler", (ExceptionHandler) new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handleException(null, null, exception);
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handleException(message, null, exception);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                handled.incrementAndGet();
                errorHandled.countDown();
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("azure-storage-blob://account/in?serviceClient=#client&moveAfterRead=true"
                     + "&destinationContainer=done&exceptionHandler=#handler&initialDelay=0&delay=60000")
                        .routeId("blob")
                        .to("mock:result");
            }
        });
        context.start();
    }

    @SuppressWarnings("unchecked")
    private BlobServiceClient serviceClient(LongRunningOperationStatus copyStatus) {
        BlobInputStream stream = mock(BlobInputStream.class);
        when(stream.getProperties()).thenReturn(mock(BlobProperties.class));

        source = mock(BlobClient.class);
        when(source.openInputStream(nullable(BlobRange.class), nullable(BlobRequestConditions.class))).thenReturn(stream);
        when(source.getBlobUrl()).thenReturn("https://account.blob.core.windows.net/in/a.txt");

        SyncPoller<BlobCopyInfo, Void> poller = mock(SyncPoller.class);
        when(poller.waitForCompletion(any(Duration.class))).thenAnswer(i -> {
            copied.countDown();
            return new PollResponse<BlobCopyInfo>(copyStatus, null);
        });
        BlobClient destination = mock(BlobClient.class);
        when(destination.beginCopy(anyString(), any(Duration.class))).thenReturn(poller);

        BlobContainerClient in = mock(BlobContainerClient.class);
        when(in.listBlobs(any(ListBlobsOptions.class), nullable(Duration.class))).thenAnswer(i -> new PagedIterable<>(
                () -> new PagedResponseBase<Void, BlobItem>(
                        null, 200, null, List.of(new BlobItem().setName("a.txt")), null, null)));
        when(in.getBlobClient("a.txt")).thenReturn(source);
        BlobContainerClient done = mock(BlobContainerClient.class);
        when(done.getBlobClient("a.txt")).thenReturn(destination);

        BlobServiceClient client = mock(BlobServiceClient.class);
        when(client.getBlobContainerClient("in")).thenReturn(in);
        when(client.getBlobContainerClient("done")).thenReturn(done);
        return client;
    }
}
