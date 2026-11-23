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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponseBase;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.core.util.Context;
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
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.options.BlockBlobSimpleUploadOptions;
import com.azure.storage.blob.specialized.BlobInputStream;
import com.azure.storage.blob.specialized.BlockBlobClient;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.builder.Builder.constant;
import static org.apache.camel.builder.Builder.simple;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * With deleteAfterRead or moveAfterRead the consumer deletes or moves the blob it consumed once the exchange is done,
 * whatever the route did with the CamelAzureStorageBlobBlobName header in the meantime: a route may set it to upload a
 * copy of the blob under another name (the producer takes the name from that header), or remove the Camel headers
 * before it calls another system.
 * <p>
 * The scheduler of the consumer is not started: the tests call {@code poll()} themselves. The routes are synchronous,
 * so the delete or the move is done when {@code poll()} returns.
 */
class BlobConsumerDeleteMoveAfterReadTest extends CamelTestSupport {

    private static final String ACCOUNT_URL = "https://account.blob.core.windows.net/";

    @BindToRegistry("client")
    private final BlobServiceClient client = mock(BlobServiceClient.class);

    private final Map<String, Set<String>> containers = new ConcurrentHashMap<>();
    private final Map<String, BlobContainerClient> containerClients = new ConcurrentHashMap<>();
    private final List<String> uploads = new CopyOnWriteArrayList<>();
    private final List<String> copies = new CopyOnWriteArrayList<>();
    private final List<String> deletes = new CopyOnWriteArrayList<>();

    @Override
    protected void doPreSetup() {
        when(client.getBlobContainerClient(anyString()))
                .thenAnswer(i -> containerClients.computeIfAbsent(i.getArgument(0), this::containerClient));
        blobs("in").add("todo/a.txt");
    }

    @Test
    void deleteAfterReadDeletesTheConsumedBlobWhenTheRouteRenamesIt() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        BlobConsumer consumer = startRoute("&deleteAfterRead=true", this::uploadCopy);

        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of("in/out/todo/a.txt"), uploads);
        assertEquals(List.of("in/todo/a.txt"), deletes);
        // the copy the route uploaded is kept
        assertTrue(blobs("in").contains("out/todo/a.txt"));
        // and the next poll does not consume the blob again
        assertEquals(0, consumer.poll());
    }

    @Test
    void deleteAfterReadDeletesTheConsumedBlobWhenTheRouteRemovesTheHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        BlobConsumer consumer = startRoute("&deleteAfterRead=true", route -> route.removeHeaders("CamelAzureStorageBlob*"));

        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of("in/todo/a.txt"), deletes);
        assertEquals(0, consumer.poll());
    }

    @Test
    void moveAfterReadMovesTheConsumedBlobWhenTheRouteRenamesIt() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        BlobConsumer consumer = startRoute("&moveAfterRead=true&destinationContainer=archive", this::uploadCopy);

        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of("in/todo/a.txt -> archive/todo/a.txt"), copies);
        assertEquals(List.of("in/todo/a.txt"), deletes);
        assertTrue(blobs("in").contains("out/todo/a.txt"));
        assertEquals(0, consumer.poll());
    }

    @Test
    void deleteAfterReadDeletesTheConsumedBlob() throws Exception {
        // control: the route keeps the header the consumer set
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(BlobConstants.BLOB_NAME, "todo/a.txt");
        BlobConsumer consumer = startRoute("&deleteAfterRead=true", route -> {
        });

        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of("in/todo/a.txt"), deletes);
        assertEquals(0, consumer.poll());
    }

    @Test
    void moveAfterReadMovesTheConsumedBlob() throws Exception {
        // control: the destination name is built from the name of the consumed blob
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        BlobConsumer consumer = startRoute("&moveAfterRead=true&destinationContainer=archive&removePrefixOnMove=true"
                                           + "&destinationBlobPrefix=done/&destinationBlobSuffix=.bak",
                route -> {
                });

        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of("in/todo/a.txt -> archive/done/a.txt.bak"), copies);
        assertEquals(List.of("in/todo/a.txt"), deletes);
    }

    /**
     * Uploads a copy of the blob to out/ in the same container: the producer takes the name of the blob from the
     * header.
     */
    private void uploadCopy(RouteDefinition route) {
        route.setHeader(BlobConstants.BLOB_NAME, simple("out/${header.CamelAzureStorageBlobBlobName}"))
                .setBody(constant("copy"))
                .to("azure-storage-blob://account/in?serviceClient=#client&operation=uploadBlockBlob");
    }

    private BlobConsumer startRoute(String options, Consumer<RouteDefinition> steps) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                RouteDefinition route = from("azure-storage-blob://account/in?serviceClient=#client&prefix=todo/"
                                             + "&startScheduler=false" + options)
                        .routeId("blob");
                steps.accept(route);
                route.to("mock:result");
            }
        });
        return (BlobConsumer) context.getRoute("blob").getConsumer();
    }

    private Set<String> blobs(String container) {
        return containers.computeIfAbsent(container, k -> new ConcurrentSkipListSet<>());
    }

    private BlobContainerClient containerClient(String container) {
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(containerClient.listBlobs(nullable(ListBlobsOptions.class), nullable(String.class), nullable(Duration.class)))
                .thenAnswer(i -> {
                    ListBlobsOptions options = i.getArgument(0);
                    String prefix = options != null && options.getPrefix() != null ? options.getPrefix() : "";
                    List<BlobItem> items = blobs(container).stream().filter(name -> name.startsWith(prefix))
                            .map(name -> new BlobItem().setName(name)).toList();
                    return new PagedIterable<>(() -> new PagedResponseBase<Void, BlobItem>(null, 200, null, items, null, null));
                });
        when(containerClient.getBlobClient(anyString())).thenAnswer(i -> blobClient(container, i.getArgument(0)));
        return containerClient;
    }

    @SuppressWarnings("unchecked")
    private BlobClient blobClient(String container, String name) {
        BlobClient blobClient = mock(BlobClient.class);
        when(blobClient.getBlobUrl()).thenReturn(ACCOUNT_URL + container + "/" + name);
        when(blobClient.openInputStream(nullable(BlobRange.class), nullable(BlobRequestConditions.class))).thenAnswer(i -> {
            if (!blobs(container).contains(name)) {
                throw notFound();
            }
            BlobInputStream stream = mock(BlobInputStream.class);
            when(stream.getProperties()).thenReturn(mock(BlobProperties.class));
            return stream;
        });
        doAnswer(i -> {
            deletes.add(container + "/" + name);
            if (!blobs(container).remove(name)) {
                throw notFound();
            }
            return null;
        }).when(blobClient).delete();
        when(blobClient.beginCopy(anyString(), any(Duration.class))).thenAnswer(i -> {
            String source = i.<String> getArgument(0).substring(ACCOUNT_URL.length());
            String sourceContainer = source.substring(0, source.indexOf('/'));
            String sourceName = source.substring(source.indexOf('/') + 1);
            copies.add(source + " -> " + container + "/" + name);
            boolean found = blobs(sourceContainer).contains(sourceName);
            if (found) {
                blobs(container).add(name);
            }
            SyncPoller<BlobCopyInfo, Void> poller = mock(SyncPoller.class);
            when(poller.waitForCompletion(any(Duration.class))).thenReturn(new PollResponse<>(
                    found ? LongRunningOperationStatus.SUCCESSFULLY_COMPLETED : LongRunningOperationStatus.FAILED, null));
            return poller;
        });
        BlockBlobClient blockBlobClient = mock(BlockBlobClient.class);
        when(blockBlobClient.uploadWithResponse(any(BlockBlobSimpleUploadOptions.class), nullable(Duration.class),
                nullable(Context.class))).thenAnswer(i -> {
                    uploads.add(container + "/" + name);
                    blobs(container).add(name);
                    return new SimpleResponse<>(null, 201, new HttpHeaders(), null);
                });
        when(blobClient.getBlockBlobClient()).thenReturn(blockBlobClient);
        return blobClient;
    }

    private static BlobStorageException notFound() {
        HttpResponse response = mock(HttpResponse.class);
        when(response.getStatusCode()).thenReturn(404);
        return new BlobStorageException("The specified blob does not exist.", response, null);
    }
}
