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
package org.apache.camel.component.azure.storage.blob.integration;

import java.util.concurrent.TimeUnit;

import com.azure.storage.blob.BlobContainerClient;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for deleteAfterRead and moveAfterRead consumer options.
 */
class BlobConsumerDeleteMoveIT extends Base {

    @EndpointInject("direct:createBlob")
    private ProducerTemplate templateStart;

    private String deleteContainerName;
    private String moveSourceContainerName;
    private String moveDestContainerName;
    private String noDeleteContainerName;
    private String prefixMoveSourceContainerName;
    private String prefixMoveDestContainerName;

    private BlobContainerClient deleteContainerClient;
    private BlobContainerClient moveSourceContainerClient;
    private BlobContainerClient moveDestContainerClient;
    private BlobContainerClient noDeleteContainerClient;
    private BlobContainerClient prefixMoveSourceContainerClient;
    private BlobContainerClient prefixMoveDestContainerClient;

    @BeforeAll
    public void setup() {
        deleteContainerName = RandomStringUtils.randomAlphabetic(5).toLowerCase();
        moveSourceContainerName = RandomStringUtils.randomAlphabetic(5).toLowerCase();
        moveDestContainerName = RandomStringUtils.randomAlphabetic(5).toLowerCase();
        noDeleteContainerName = RandomStringUtils.randomAlphabetic(5).toLowerCase();
        prefixMoveSourceContainerName = RandomStringUtils.randomAlphabetic(5).toLowerCase();
        prefixMoveDestContainerName = RandomStringUtils.randomAlphabetic(5).toLowerCase();

        deleteContainerClient = serviceClient.getBlobContainerClient(deleteContainerName);
        moveSourceContainerClient = serviceClient.getBlobContainerClient(moveSourceContainerName);
        moveDestContainerClient = serviceClient.getBlobContainerClient(moveDestContainerName);
        noDeleteContainerClient = serviceClient.getBlobContainerClient(noDeleteContainerName);
        prefixMoveSourceContainerClient = serviceClient.getBlobContainerClient(prefixMoveSourceContainerName);
        prefixMoveDestContainerClient = serviceClient.getBlobContainerClient(prefixMoveDestContainerName);

        // Create all containers
        deleteContainerClient.create();
        moveSourceContainerClient.create();
        moveDestContainerClient.create();
        noDeleteContainerClient.create();
        prefixMoveSourceContainerClient.create();
        prefixMoveDestContainerClient.create();
    }

    @Test
    void testDeleteAfterRead() throws Exception {
        final String blobName = "delete-test-blob";
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:deleteResult");
        mockEndpoint.expectedMessageCount(1);

        // Create a blob
        templateStart.send("direct:createBlob", exchange -> {
            exchange.getIn().setBody("Test content for delete");
            exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, deleteContainerName);
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName);
        });

        // Verify blob exists before consumption
        assertTrue(deleteContainerClient.getBlobClient(blobName).exists(),
                "Blob should exist before consumption");

        mockEndpoint.assertIsSatisfied();

        // Wait for delete operation to complete
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertFalse(deleteContainerClient.getBlobClient(blobName).exists(),
                        "Blob should be deleted after consumption"));
    }

    @Test
    void testNoDeleteAfterRead() throws Exception {
        final String blobName = "no-delete-test-blob";
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:noDeleteResult");
        mockEndpoint.expectedMessageCount(1);

        // Create a blob
        templateStart.send("direct:createBlob", exchange -> {
            exchange.getIn().setBody("Test content no delete");
            exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, noDeleteContainerName);
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName);
        });

        // Verify blob exists before consumption
        assertTrue(noDeleteContainerClient.getBlobClient(blobName).exists(),
                "Blob should exist before consumption");

        mockEndpoint.assertIsSatisfied();

        // Wait a bit to ensure no delete happens
        Thread.sleep(2000);

        // Verify blob still exists (default deleteAfterRead=false)
        assertTrue(noDeleteContainerClient.getBlobClient(blobName).exists(),
                "Blob should still exist after consumption when deleteAfterRead=false");
    }

    @Test
    void testMoveAfterRead() throws Exception {
        final String blobName = "move-test-blob";
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:moveResult");
        mockEndpoint.expectedMessageCount(1);

        // Create a blob in source container
        templateStart.send("direct:createBlob", exchange -> {
            exchange.getIn().setBody("Test content for move");
            exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, moveSourceContainerName);
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName);
        });

        // Verify blob exists in source before consumption
        assertTrue(moveSourceContainerClient.getBlobClient(blobName).exists(),
                "Blob should exist in source before consumption");
        assertFalse(moveDestContainerClient.getBlobClient(blobName).exists(),
                "Blob should not exist in destination before consumption");

        mockEndpoint.assertIsSatisfied();

        // Wait for move operation to complete
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertFalse(moveSourceContainerClient.getBlobClient(blobName).exists(),
                            "Blob should be deleted from source after move");
                    assertTrue(moveDestContainerClient.getBlobClient(blobName).exists(),
                            "Blob should exist in destination after move");
                });
    }

    @Test
    void testMoveAfterReadWithPrefixAndSuffix() throws Exception {
        final String sourceBlobName = "incoming/test-blob";
        final String expectedDestBlobName = "processed/test-blob.done";
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:prefixMoveResult");
        mockEndpoint.expectedMessageCount(1);

        // Create a blob in source container with prefix
        templateStart.send("direct:createBlob", exchange -> {
            exchange.getIn().setBody("Test content for prefix move");
            exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, prefixMoveSourceContainerName);
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, sourceBlobName);
        });

        // Verify blob exists in source before consumption
        assertTrue(prefixMoveSourceContainerClient.getBlobClient(sourceBlobName).exists(),
                "Blob should exist in source before consumption");

        mockEndpoint.assertIsSatisfied();

        // Wait for move operation to complete
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertFalse(prefixMoveSourceContainerClient.getBlobClient(sourceBlobName).exists(),
                            "Blob should be deleted from source after move");
                    assertTrue(prefixMoveDestContainerClient.getBlobClient(expectedDestBlobName).exists(),
                            "Blob should exist in destination with new name after move");
                });
    }

    @AfterAll
    public void deleteContainers() {
        deleteContainerClient.deleteIfExists();
        moveSourceContainerClient.deleteIfExists();
        moveDestContainerClient.deleteIfExists();
        noDeleteContainerClient.deleteIfExists();
        prefixMoveSourceContainerClient.deleteIfExists();
        prefixMoveDestContainerClient.deleteIfExists();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createBlob")
                        .to("azure-storage-blob://cameldev?operation=uploadBlockBlob");

                // Route with deleteAfterRead=true
                from("azure-storage-blob://cameldev/" + deleteContainerName
                     + "?blobServiceClient=#serviceClient&deleteAfterRead=true")
                        .to("mock:deleteResult");

                // Route with default deleteAfterRead=false (no delete)
                from("azure-storage-blob://cameldev/" + noDeleteContainerName
                     + "?blobServiceClient=#serviceClient")
                        .to("mock:noDeleteResult");

                // Route with moveAfterRead=true
                from("azure-storage-blob://cameldev/" + moveSourceContainerName
                     + "?blobServiceClient=#serviceClient&moveAfterRead=true&destinationContainer=" + moveDestContainerName)
                        .to("mock:moveResult");

                // Route with moveAfterRead=true and prefix/suffix options
                from("azure-storage-blob://cameldev/" + prefixMoveSourceContainerName
                     + "?blobServiceClient=#serviceClient"
                     + "&moveAfterRead=true"
                     + "&destinationContainer=" + prefixMoveDestContainerName
                     + "&prefix=incoming/"
                     + "&removePrefixOnMove=true"
                     + "&destinationBlobPrefix=processed/"
                     + "&destinationBlobSuffix=.done")
                        .to("mock:prefixMoveResult");
            }
        };
    }
}
