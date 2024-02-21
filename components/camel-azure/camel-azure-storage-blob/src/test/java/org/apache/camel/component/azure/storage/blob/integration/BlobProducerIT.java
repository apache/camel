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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.PageRange;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.storage.blob.BlobBlock;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlobProducerIT extends Base {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;
    private String resultName = "mock:result";
    private BlobContainerClient containerClient;

    @BeforeAll
    public void prepare() {
        // create test container
        containerClient = serviceClient.getBlobContainerClient(containerName);
        containerClient.create();
    }

    @Test
    void testUploadBlockBlob() throws InterruptedException {
        final String blobName = RandomStringUtils.randomAlphabetic(10);

        result.expectedMessageCount(1);

        template.send("direct:uploadBlockBlob", exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName);
            exchange.getIn().setBody("Block Blob");
        });

        result.assertIsSatisfied();
    }

    @Test
    void testCommitAndStageBlockBlob() throws InterruptedException {
        final String blobName = RandomStringUtils.randomAlphabetic(10);

        result.expectedMessageCount(1);

        result.expectedBodiesReceived(true);

        template.send("direct:stageBlockBlobList", exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName);
            exchange.getIn().setHeader(BlobConstants.COMMIT_BLOCK_LIST_LATER, false);

            final List<BlobBlock> blocks = new LinkedList<>();
            blocks.add(BlobBlock.createBlobBlock(new ByteArrayInputStream("Hello".getBytes())));
            blocks.add(BlobBlock.createBlobBlock(new ByteArrayInputStream("From".getBytes())));
            blocks.add(BlobBlock.createBlobBlock(new ByteArrayInputStream("Camel".getBytes())));

            exchange.getIn().setBody(blocks);
        });

        result.assertIsSatisfied();

        assertNotNull(result.getExchanges().get(0).getMessage().getHeader(BlobConstants.E_TAG));
    }

    @Test
    void testCommitAppendBlobWithError() throws InterruptedException {
        final String blobName = RandomStringUtils.randomAlphabetic(10);

        template.send("direct:commitAppendBlobWithError", exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName);
            exchange.getIn().setHeader(BlobConstants.CREATE_APPEND_BLOB, false);

            final String data = "Hello world from my awesome tests!";
            final InputStream dataStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));

            exchange.getIn().setBody(dataStream);
        });

        result.assertIsSatisfied();

        // append blob not created because of the flag
        assertTrue(result.getExchanges().isEmpty());
    }

    @Test
    void testCreateAndUpdateAppendBlob() throws InterruptedException {
        final String blobName = RandomStringUtils.randomAlphabetic(10);

        result.expectedMessageCount(1);

        result.expectedBodiesReceived(true);

        template.send("direct:commitAppendBlob", exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName);

            final String data = "Hello world from my awesome tests!";
            final InputStream dataStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));

            exchange.getIn().setBody(dataStream);
        });

        result.assertIsSatisfied();

        assertNotNull(result.getExchanges().get(0).getMessage().getHeader(BlobConstants.E_TAG));
        assertNotNull(result.getExchanges().get(0).getMessage().getHeader(BlobConstants.COMMITTED_BLOCK_COUNT));
    }

    @Test
    void testCreateAndUploadPageBlob() throws InterruptedException {
        final String blobName = RandomStringUtils.randomAlphabetic(10);

        result.expectedMessageCount(1);

        result.expectedBodiesReceived(true);

        template.send("direct:uploadPageBlob", exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName);

            byte[] dataBytes = new byte[512]; // we set range for the page from 0-511
            new SecureRandom().nextBytes(dataBytes);
            final InputStream dataStream = new ByteArrayInputStream(dataBytes);
            final PageRange pageRange = new PageRange().setStart(0).setEnd(511);

            exchange.getIn().setHeader(BlobConstants.PAGE_BLOB_RANGE, pageRange);
            exchange.getIn().setBody(dataStream);
        });

        result.assertIsSatisfied();

        assertNotNull(result.getExchanges().get(0).getMessage().getHeader(BlobConstants.E_TAG));
    }

    @Test
    void testUploadBlockBlobWithConfigUri() throws InterruptedException {
        result.expectedMessageCount(1);

        template.send("direct:uploadBlockBlobWithConfigUri",
                exchange -> exchange.getIn().setBody("Block Blob"));

        result.assertIsSatisfied();
    }

    @Test
    void testHeaderPreservation() throws InterruptedException {
        result.expectedMessageCount(1);

        template.send("direct:uploadBlockBlobWithConfigUri",
                exchange -> {
                    exchange.getIn().setBody("Block Blob");
                    exchange.getIn().setHeader("DoNotDelete", "keep me");
                });
        assertEquals("keep me", result.getExchanges().get(0).getMessage().getHeader("DoNotDelete"));

        result.assertIsSatisfied();
    }

    @AfterAll
    public void deleteClient() {
        containerClient.delete();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:uploadBlockBlob")
                        .to(componentUri("uploadBlockBlob"))
                        .to(resultName);

                from("direct:stageBlockBlobList")
                        .to(componentUri("stageBlockBlobList"))
                        .to(resultName);

                from("direct:commitAppendBlob")
                        .to(componentUri("commitAppendBlob"))
                        .to(resultName);

                from("direct:commitAppendBlobWithError")
                        .to(componentUri("commitAppendBlob"))
                        .to(resultName);

                from("direct:uploadPageBlob")
                        .to(componentUri("uploadPageBlob"))
                        .to(resultName);

                from("direct:uploadBlockBlobWithConfigUri")
                        .to(componentUri("uploadBlockBlob") + "&blobName=uploadBlockName")
                        .to(resultName);
            }
        };
    }

    private String componentUri(final String operation) {
        return String.format("azure-storage-blob://cameldev/%s?operation=%s", containerName,
                operation);
    }
}
