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
package org.apache.camel.component.azure.storage.blob.transform;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponseBase;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.specialized.BlobInputStream;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The CloudEvent source of the blobs of the azure-storage-blob consumer, when the transformer runs in another route, as
 * with the output data type of a Kamelet: that route gets a copy of the exchange with the headers of the consumer, and
 * its own from endpoint.
 */
class AzureStorageBlobCloudEventDataTypeTransformerRouteTest extends CamelTestSupport {

    @BindToRegistry("client")
    private final BlobServiceClient serviceClient = mock(BlobServiceClient.class);

    @Override
    protected void doPreSetup() throws Exception {
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(serviceClient.getBlobContainerClient("invoices")).thenReturn(containerClient);
        AtomicBoolean listed = new AtomicBoolean();
        when(containerClient.listBlobs(nullable(ListBlobsOptions.class), nullable(String.class), nullable(Duration.class)))
                .thenAnswer(i -> {
                    // two blobs on the first poll, and none after
                    List<BlobItem> items = listed.getAndSet(true)
                            ? List.of()
                            : List.of(new BlobItem().setName("a.txt"), new BlobItem().setName("b.txt"));
                    return new PagedIterable<>(() -> new PagedResponseBase<Void, BlobItem>(null, 200, null, items, null, null));
                });
        when(containerClient.getBlobClient(anyString())).thenAnswer(i -> {
            BlobClient blobClient = mock(BlobClient.class);
            BlobInputStream stream = mock(BlobInputStream.class);
            when(stream.getProperties()).thenReturn(mock(BlobProperties.class));
            when(blobClient.openInputStream(nullable(BlobRange.class), nullable(BlobRequestConditions.class)))
                    .thenReturn(stream);
            return blobClient;
        });
    }

    @Test
    void theSourceIsTheContainerOfTheConsumer() throws Exception {
        // CAMEL-27101: the source was built from the ETag of the blob, so every event had a source of its own
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedHeaderValuesReceivedInAnyOrder(BlobConstants.BLOB_NAME, "a.txt", "b.txt");
        result.allMessages().header(BlobConstants.CONSUMED_CONTAINER_NAME).isEqualTo("invoices");
        result.allMessages().header(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE).isEqualTo("azure.storage.blob.invoices");
        // the container name header of the producer is not set, so a producer in the route does not write to this
        // container
        result.allMessages().header(BlobConstants.BLOB_CONTAINER_NAME).isNull();

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // seda hands a copy of the exchange to the next route, as the sink of a Kamelet does
                from("azure-storage-blob://account/invoices?serviceClient=#client&initialDelay=0&delay=50")
                        .to("seda:next");

                from("seda:next")
                        .transformDataType("azure-storage-blob:application-cloudevents")
                        .to("mock:result");
            }
        };
    }
}
