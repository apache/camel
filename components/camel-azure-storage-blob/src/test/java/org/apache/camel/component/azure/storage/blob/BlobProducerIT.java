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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.PageRange;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.storage.blob.client.BlobClientFactory;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BlobProducerIT extends CamelTestSupport {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;
    private String resultName = "mock:result";

    private String containerName;

    private BlobContainerClient containerClient;

    @BeforeAll
    public void prepare() throws Exception {
        containerName = RandomStringUtils.randomAlphabetic(5).toLowerCase();

        BlobConfiguration configuration = new BlobConfiguration();
        configuration.setCredentials(storageSharedKeyCredential());
        configuration.setContainerName(containerName);

        final BlobServiceClient serviceClient = BlobClientFactory.createBlobServiceClient(configuration);
        containerClient = serviceClient.getBlobContainerClient(containerName);

        // create test container
        containerClient.create();
    }

    @Test
    public void testUploadBlockBlob() throws InterruptedException {
        final String blobName = RandomStringUtils.randomAlphabetic(10);

        result.expectedMessageCount(1);

        template.send("direct:uploadBlockBlob", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName);
            exchange.getIn().setBody("Block Blob");
        });

        result.assertIsSatisfied();
    }

    @Test
    public void testCommitAndStageBlockBlob() throws InterruptedException, IOException {
        final String blobName = RandomStringUtils.randomAlphabetic(10);

        result.expectedMessageCount(1);

        result.expectedBodiesReceived(true);

        template.send("direct:stageBlockBlobList", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName);

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
    public void testCreateAndUpdateAppendBlob() throws InterruptedException {
        final String blobName = RandomStringUtils.randomAlphabetic(10);

        result.expectedMessageCount(1);

        result.expectedBodiesReceived(true);

        template.send("direct:commitAppendBlob", ExchangePattern.InOnly, exchange -> {
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
    public void testCreateAndUploadPageBlob() throws InterruptedException {
        final String blobName = RandomStringUtils.randomAlphabetic(10);

        result.expectedMessageCount(1);

        result.expectedBodiesReceived(true);

        template.send("direct:uploadPageBlob", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName);

            byte[] dataBytes = new byte[512]; // we set range for the page from 0-511
            new Random().nextBytes(dataBytes);
            final InputStream dataStream = new ByteArrayInputStream(dataBytes);
            final PageRange pageRange = new PageRange().setStart(0).setEnd(511);

            exchange.getIn().setHeader(BlobConstants.PAGE_BLOB_RANGE, pageRange);
            exchange.getIn().setBody(dataStream);
        });

        result.assertIsSatisfied();

        assertNotNull(result.getExchanges().get(0).getMessage().getHeader(BlobConstants.E_TAG));
    }


    @AfterAll
    public void tearDown() {
        containerClient.delete();
    }


    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("creds", storageSharedKeyCredential());
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:uploadBlockBlob")
                        .to(componentUri("uploadBlockBlob"))
                        .to(resultName);

                from("direct:stageBlockBlobList")
                        .to(componentUri("stageBlockBlobList"))
                        .to(resultName);

                from("direct:commitAppendBlob")
                        .to(componentUri("commitAppendBlob"))
                        .to(resultName);

                from("direct:uploadPageBlob")
                        .to(componentUri("uploadPageBlob"))
                        .to(resultName);
            }
        };
    }

    private StorageSharedKeyCredential storageSharedKeyCredential() throws Exception {
        final Properties properties = BlobTestUtils.loadAzureAccessFromJvmEnv();
        return new StorageSharedKeyCredential(properties.getProperty("account_name"), properties.getProperty("access_key"));
    }

    private String componentUri(final String operation) {
        return String.format("azure-storage-blob://cameldev/%s?credentials=#creds&operation=%s", containerName, operation);
    }
}
