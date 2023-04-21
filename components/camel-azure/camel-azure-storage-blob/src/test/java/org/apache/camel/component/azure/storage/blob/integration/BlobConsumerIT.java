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
import java.util.regex.Pattern;

import com.azure.storage.blob.BlobContainerClient;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
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

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
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
    private String blobName;
    private String blobName2;

    private BlobContainerClient containerClient;
    private BlobContainerClient batchContainerClient;
    private BlobContainerClient prefixContainerClient;
    private final String regex = ".*\\.pdf";
    private final String prefix = "blob-prefix";

    @BeforeAll
    public void setup() {
        batchContainerName = RandomStringUtils.randomAlphabetic(5).toLowerCase();
        prefixContainerName = RandomStringUtils.randomAlphabetic(5).toLowerCase();
        blobName = RandomStringUtils.randomAlphabetic(5);
        blobName2 = RandomStringUtils.randomAlphabetic(5);

        containerClient = serviceClient.getBlobContainerClient(containerName);
        batchContainerClient = serviceClient.getBlobContainerClient(batchContainerName);
        prefixContainerClient = serviceClient.getBlobContainerClient(prefixContainerName);

        // create test container
        containerClient.create();
        batchContainerClient.create();
        prefixContainerClient.create();
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

        Pattern pattern = Pattern.compile(regex);
        for (Exchange e : mockEndpoint.getExchanges()) {
            String blobName = e.getIn().getHeader(BlobConstants.BLOB_NAME, String.class);
            assertTrue(pattern.matcher(blobName).matches());
        }
    }

    @Test
    void testPrefixBasedPolling() throws InterruptedException {
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:resultPrefix");
        mockEndpoint.expectedMessageCount(1);

        templateStart.send("direct:createBlob", exchange -> {
            exchange.getIn().setBody("Blob 1");
            exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, prefixContainerName);
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, prefix + "/test_blob_1");
        });

        templateStart.send("direct:createBlob", exchange -> {
            exchange.getIn().setBody("Blob 2");
            exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, prefixContainerName);
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, "non_prefixed_blob");
        });

        mockEndpoint.assertIsSatisfied();

        String text = mockEndpoint.getExchanges().get(0).getIn().getBody(String.class);

        assertEquals("Blob 1", text);
        assertEquals(1, mockEndpoint.getExchanges().size());
    }

    private String generateRandomBlobName(String prefix, String extension) {
        return prefix + randomAlphabetic(5).toLowerCase() + "." + extension;
    }

    @AfterAll
    public void tearDown() {
        // delete container
        containerClient.delete();
        batchContainerClient.delete();
        prefixContainerClient.delete();
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
                     + "?prefix=aaaa&regex=" + regex)
                        .idempotentConsumer(body(), new MemoryIdempotentRepository())
                        .to("mock:resultRegex");

                from("azure-storage-blob://cameldev/" + prefixContainerName + "?prefix=" + prefix)
                        .to("mock:resultPrefix");
            }
        };
    }
}
