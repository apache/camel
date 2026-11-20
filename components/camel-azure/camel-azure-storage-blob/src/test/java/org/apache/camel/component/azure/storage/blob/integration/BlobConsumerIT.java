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

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.azure.storage.blob.BlobContainerClient;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlobConsumerIT extends Base {

    @TempDir
    static Path testDir;
    @EndpointInject("direct:start")
    private ProducerTemplate templateStart;
    private String batchContainerName;
    private String prefixContainerName;
    private String limitedContainerName;
    private String blobName;
    private String blobName2;

    private BlobContainerClient containerClient;
    private BlobContainerClient batchContainerClient;
    private BlobContainerClient prefixContainerClient;
    private BlobContainerClient limitedContainerClient;
    private final String regex = ".*\\.pdf";
    private final String prefix = "blob-prefix";
    private String retainedContainerName;

    @BeforeAll
    public void setup() {
        batchContainerName = RandomStringUtils.secure().nextAlphabetic(5).toLowerCase();
        prefixContainerName = RandomStringUtils.secure().nextAlphabetic(5).toLowerCase();
        limitedContainerName = RandomStringUtils.secure().nextAlphabetic(5).toLowerCase();
        blobName = RandomStringUtils.secure().nextAlphabetic(5);
        blobName2 = RandomStringUtils.secure().nextAlphabetic(5);

        containerClient = serviceClient.getBlobContainerClient(containerName);
        batchContainerClient = serviceClient.getBlobContainerClient(batchContainerName);
        prefixContainerClient = serviceClient.getBlobContainerClient(prefixContainerName);
        limitedContainerClient = serviceClient.getBlobContainerClient(limitedContainerName);

        // create test container
        containerClient.create();
        batchContainerClient.create();
        prefixContainerClient.create();
        limitedContainerClient.create();
        retainedContainerName = "retained-" + RandomStringUtils.secure().nextAlphabetic(8).toLowerCase();
        serviceClient.getBlobContainerClient(retainedContainerName).create();
    }

    @Test
    void testPollingToFile() throws Exception {
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);

        templateStart.send("direct:createBlob", exchange -> {
            exchange.getIn().setBody("Block Blob");
            exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, containerName);
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName);
        });

        mockEndpoint.assertIsSatisfied();

        final File file = mockEndpoint.getExchanges().get(0).getIn().getBody(File.class);
        assertNotNull(file, "File must be set");
        assertEquals("Block Blob", context().getTypeConverter().convertTo(String.class, file));
    }

    @Test
    void testPollingToInputStream() throws Exception {
        templateStart.send("direct:createBlob", exchange -> {
            exchange.getIn().setBody("Block Blob");
            exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, containerName);
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName2);
        });

        final MockEndpoint mockEndpoint = getMockEndpoint("mock:resultOutputStream");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.assertIsSatisfied();

        String text = mockEndpoint.getExchanges().get(0).getIn().getBody(String.class);
        assertEquals("Block Blob", text);
    }

    @Test
    void testBatchFilePolling() throws Exception {
        // test output stream based
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:resultBatch");
        mockEndpoint.expectedMessageCount(2);

        // test file based
        final MockEndpoint mockEndpointFile = getMockEndpoint("mock:resultBatchFile");
        mockEndpointFile.expectedMessageCount(2);

        templateStart.send("direct:createBlob", exchange -> {
            exchange.getIn().setBody("Block Batch Blob 1");
            exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, batchContainerName);
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, "test_batch_blob_1");
        });

        templateStart.send("direct:createBlob", exchange -> {
            exchange.getIn().setBody("Block Batch Blob 2");
            exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, batchContainerName);
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, "test_batch_blob_2");
        });

        MockEndpoint.assertIsSatisfied(context());

        String text = mockEndpoint.getExchanges().get(0).getIn().getBody(String.class);
        String text2 = mockEndpoint.getExchanges().get(1).getIn().getBody(String.class);
        assertEquals("Block Batch Blob 1", text);
        assertEquals("Block Batch Blob 2", text2);

        final File file = mockEndpointFile.getExchanges().get(0).getIn().getBody(File.class);
        final File file2 = mockEndpointFile.getExchanges().get(1).getIn().getBody(File.class);
        assertNotNull(file, "File must be set");
        assertNotNull(file2, "File must be set");
        assertEquals("Block Batch Blob 1", context().getTypeConverter().convertTo(String.class, file));
        assertEquals("Block Batch Blob 2", context().getTypeConverter().convertTo(String.class, file2));
    }

    @Test
    void testRegexPolling() throws Exception {
        // test regex based
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:resultRegex");
        mockEndpoint.expectedMessageCount(15);

        // create pdf blobs
        for (int i = 0; i < 10; i++) {
            final int index = i;
            templateStart.send("direct:createBlob", exchange -> {
                exchange.getIn().setBody("Block Batch PDF Blob with RegEx Test: " + index);
                exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, batchContainerName);
                exchange.getIn().setHeader(BlobConstants.BLOB_NAME, generateRandomBlobName("regexp-test_batch_blob_", "pdf"));
            });
        }

        for (int i = 0; i < 5; i++) {
            final int index = i;
            templateStart.send("direct:createBlob", exchange -> {
                exchange.getIn().setBody("Block Batch PDF Blob with Prefix Test: " + index);
                exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, batchContainerName);
                exchange.getIn().setHeader(BlobConstants.BLOB_NAME, generateRandomBlobName("aaaa-test_batch_blob_", "pdf"));
            });
        }

        // create docx blobs
        for (int i = 0; i < 20; i++) {
            final int index = i;
            templateStart.send("direct:createBlob", exchange -> {
                exchange.getIn().setBody("Block Batch DOCX Blob Test: " + index);
                exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, batchContainerName);
                exchange.getIn().setHeader(BlobConstants.BLOB_NAME, generateRandomBlobName("regexp-test_batch_blob_", "docx"));
            });
        }

        mockEndpoint.assertIsSatisfied();

        long blobsMatchingTheIgnoredPrefix = mockEndpoint.getExchanges().stream()
                .map(exchange -> exchange.getIn().getHeader(BlobConstants.BLOB_NAME, String.class))
                .filter(name -> name.startsWith("aaaa-"))
                .count();
        assertEquals(5L, blobsMatchingTheIgnoredPrefix,
                "regex must take precedence over the configured prefix");

        for (Exchange exchange : mockEndpoint.getExchanges()) {
            Integer batchSize = (Integer) exchange.getProperty(ExchangePropertyKey.BATCH_SIZE);
            assertNotNull(batchSize);
            assertTrue(batchSize <= 2, "regex results must respect maxMessagesPerPoll");
        }

        Pattern pattern = Pattern.compile(regex);
        for (Exchange e : mockEndpoint.getExchanges()) {
            String blobName = e.getIn().getHeader(BlobConstants.BLOB_NAME, String.class);
            assertTrue(pattern.matcher(blobName).matches());
        }
    }

    @Test
    void testPrefixBasedPolling() throws Exception {
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:resultPrefix");
        mockEndpoint.expectedMessageCount(2);
        mockEndpoint.expectedHeaderValuesReceivedInAnyOrder(
                BlobConstants.BLOB_NAME, prefix + "/test_blob_1", prefix + "/test_blob_2");

        templateStart.send("direct:createBlob", exchange -> {
            exchange.getIn().setBody("Blob 1");
            exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, prefixContainerName);
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, prefix + "/test_blob_1");
        });

        templateStart.send("direct:createBlob", exchange -> {
            exchange.getIn().setBody("Blob 2");
            exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, prefixContainerName);
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, prefix + "/test_blob_2");
        });

        // This blob must not be returned by the server-side prefix filter.
        templateStart.send("direct:createBlob", exchange -> {
            exchange.getIn().setBody("Not prefixed");
            exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, prefixContainerName);
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, "non_prefixed_blob");
        });

        mockEndpoint.assertIsSatisfied();

        assertEquals(2, mockEndpoint.getExchanges().size());
        for (Exchange exchange : mockEndpoint.getExchanges()) {
            String name = exchange.getIn().getHeader(BlobConstants.BLOB_NAME, String.class);
            assertTrue(name.startsWith(prefix + "/"));
            assertEquals(1, exchange.getProperty(ExchangePropertyKey.BATCH_SIZE));
            assertEquals(0, exchange.getProperty(ExchangePropertyKey.BATCH_INDEX));
            assertEquals(Boolean.TRUE, exchange.getProperty(ExchangePropertyKey.BATCH_COMPLETE));
        }
    }

    @Test
    void testMaxMessagesPerPollAndBatchProperties() throws Exception {
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:resultLimited");
        mockEndpoint.expectedMessageCount(5);
        mockEndpoint.setResultWaitTime(30000);

        // The consumer route has an initial delay, so all five blobs are present
        // before its first poll begins.
        for (int i = 0; i < 5; i++) {
            final int index = i;
            templateStart.send("direct:createBlob", exchange -> {
                exchange.getIn().setBody("Limited Batch Blob " + index);
                exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, limitedContainerName);
                exchange.getIn().setHeader(BlobConstants.BLOB_NAME, "limited_blob_" + index);
            });
        }

        context().getRouteController().startRoute("limitedBlobTest");
        try {
            mockEndpoint.assertIsSatisfied();

            List<Integer> completedBatchSizes = mockEndpoint.getExchanges().stream()
                    .filter(exchange -> Boolean.TRUE.equals(
                            exchange.getProperty(ExchangePropertyKey.BATCH_COMPLETE)))
                    .map(exchange -> (Integer) exchange.getProperty(ExchangePropertyKey.BATCH_SIZE))
                    .toList();

            assertEquals(List.of(2, 2, 1), completedBatchSizes,
                    "five blobs with a limit of two must be routed as batches of 2, 2 and 1");

            long uniqueBlobNames = mockEndpoint.getExchanges().stream()
                    .map(exchange -> exchange.getIn().getHeader(BlobConstants.BLOB_NAME, String.class))
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            assertEquals(5L, uniqueBlobNames, "continuation across pages must not lose or duplicate blobs");

            for (Exchange exchange : mockEndpoint.getExchanges()) {
                Integer batchSize = (Integer) exchange.getProperty(ExchangePropertyKey.BATCH_SIZE);
                Integer batchIndex = (Integer) exchange.getProperty(ExchangePropertyKey.BATCH_INDEX);
                assertNotNull(batchSize);
                assertNotNull(batchIndex);
                assertTrue(batchIndex >= 0 && batchIndex < batchSize);
                assertEquals(batchIndex == batchSize - 1,
                        Boolean.TRUE.equals(exchange.getProperty(ExchangePropertyKey.BATCH_COMPLETE)));
            }
        } finally {
            context().getRouteController().stopRoute("limitedBlobTest");
        }
    }

    @Test
    void testRetainedBlobsAdvanceAcrossPollsAndWrapAround() throws Exception {
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:resultRetained");
        mockEndpoint.expectedMinimumMessageCount(6);
        mockEndpoint.setResultWaitTime(30_000);

        // This endpoint deliberately has neither deleteAfterRead nor moveAfterRead enabled.
        // A two-message batch and two-item page force the cursor to cross page boundaries.
        for (int i = 0; i < 5; i++) {
            final String name = String.format("wrap-%02d", i);
            templateStart.send("direct:createBlob", exchange -> {
                exchange.getIn().setBody("Retained blob " + name);
                exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, retainedContainerName);
                exchange.getIn().setHeader(BlobConstants.BLOB_NAME, name);
            });
        }

        // Start only after all five blobs exist, so the first listing is a stable test fixture.
        context().getRouteController().startRoute("retainedBlobCursorTest");
        try {
            mockEndpoint.assertIsSatisfied();

            final var exchanges = mockEndpoint.getExchanges();
            assertTrue(exchanges.size() >= 6, "Expected the retained listing to wrap to its first blob");

            final Set<String> firstPassNames = new HashSet<>();
            for (int i = 0; i < 5; i++) {
                final String name = exchanges.get(i).getIn().getHeader(BlobConstants.BLOB_NAME, String.class);
                assertNotNull(name, "The consumer must set the blob name header");
                firstPassNames.add(name);
            }
            assertEquals(5, firstPassNames.size(), "Every retained blob must be visited before wrapping");
            for (int i = 0; i < 5; i++) {
                assertTrue(firstPassNames.contains(String.format("wrap-%02d", i)),
                        "The first listing cycle must contain every uploaded blob");
            }

            final String firstName = exchanges.get(0).getIn().getHeader(BlobConstants.BLOB_NAME, String.class);
            final String wrappedName = exchanges.get(5).getIn().getHeader(BlobConstants.BLOB_NAME, String.class);
            assertEquals(firstName, wrappedName, "After the last page, the listing must wrap to its first blob");
        } finally {
            context().getRouteController().stopRoute("retainedBlobCursorTest");
        }

        mockEndpoint.reset();
        mockEndpoint.expectedMinimumMessageCount(1);
        mockEndpoint.setResultWaitTime(30_000);

        context().getRouteController().startRoute("retainedBlobCursorTest");
        try {
            mockEndpoint.assertIsSatisfied();
            String restartedBlobName = mockEndpoint.getExchanges().get(0).getIn()
                    .getHeader(BlobConstants.BLOB_NAME, String.class);
            assertEquals("wrap-00", restartedBlobName,
                    "restarting the route must reset the listing cursor to the first page");
        } finally {
            context().getRouteController().stopRoute("retainedBlobCursorTest");
        }
    }

    private String generateRandomBlobName(String prefix, String extension) {
        return prefix + RandomStringUtils.secure().nextAlphabetic(5).toLowerCase() + "." + extension;
    }

    @AfterAll
    public void deleteContainers() {
        // delete container
        containerClient.deleteIfExists();
        batchContainerClient.deleteIfExists();
        prefixContainerClient.deleteIfExists();
        limitedContainerClient.deleteIfExists();
        if (retainedContainerName != null) {
            serviceClient.getBlobContainerClient(retainedContainerName).deleteIfExists();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createBlob")
                        .to("azure-storage-blob://cameldev?operation=uploadBlockBlob");

                from("azure-storage-blob://cameldev/" + containerName + "?blobName=" + blobName
                     + "&blobServiceClient=#serviceClient&fileDir="
                     + testDir.toString()).to("mock:result");

                from("azure-storage-blob://cameldev/" + containerName + "?blobName=" + blobName2
                     + "&blobServiceClient=#serviceClient")
                        .to("mock:resultOutputStream");

                from("azure-storage-blob://cameldev/" + batchContainerName)
                        .to("mock:resultBatch");

                from("azure-storage-blob://cameldev/" + batchContainerName + "?fileDir="
                     + testDir.toString()).to("mock:resultBatchFile");

                // if regex is set then prefix should have no effect
                from("azure-storage-blob://cameldev/" + batchContainerName
                     + "?prefix=aaaa&regex=" + regex + "&maxMessagesPerPoll=2&maxResultsPerPage=3")
                        .idempotentConsumer(body(), new MemoryIdempotentRepository())
                        .to("mock:resultRegex");

                from("azure-storage-blob://cameldev/" + prefixContainerName + "?prefix=" + prefix
                     + "&maxMessagesPerPoll=1&maxResultsPerPage=1&deleteAfterRead=true")
                        .to("mock:resultPrefix");

                from("azure-storage-blob://cameldev/" + limitedContainerName
                     + "?maxMessagesPerPoll=2&maxResultsPerPage=2&deleteAfterRead=true&initialDelay=0&delay=100")
                        .routeId("limitedBlobTest").autoStartup(false)
                        .to("mock:resultLimited");
                from("azure-storage-blob://cameldev/" + retainedContainerName
                     + "?maxMessagesPerPoll=2&maxResultsPerPage=2&delay=250&initialDelay=0")
                        .routeId("retainedBlobCursorTest").autoStartup(false)
                        .to("mock:resultRetained");
            }
        };
    }
}
