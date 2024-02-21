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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.changefeed.BlobChangefeedClient;
import com.azure.storage.blob.changefeed.BlobChangefeedClientBuilder;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.BlobUtils;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BlobChangeFeedOperationsIT extends Base {

    private BlobChangefeedClient client;
    private BlobContainerClient containerClient;

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;
    private String resultName = "mock:result";

    @BeforeAll
    public void setupClient() {
        client = new BlobChangefeedClientBuilder(serviceClient)
                .buildClient();

        // create test container
        containerClient = serviceClient.getBlobContainerClient(containerName);
        containerClient.create();
    }

    @Test
    @Disabled("It is disabled due to changefeed support in the default test account")
    void testGetChangeFeed() throws IOException, InterruptedException {
        // create test blobs
        final String blobName = RandomStringUtils.randomAlphabetic(10);
        final String data = "Hello world from my awesome tests!";
        final InputStream dataStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));

        containerClient.getBlobClient(blobName).getBlockBlobClient().upload(dataStream,
                BlobUtils.getInputStreamLength(dataStream));

        result.expectedMessageCount(1);

        // test feed
        template.send("direct:getChangeFeed", exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName);
            exchange.getIn().setBody("test");
        });

        result.assertIsSatisfied(1000);

        // we have events
        assertNotNull(result.getExchanges().get(0).getMessage().getBody());
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
                from("direct:getChangeFeed")
                        .to("azure-storage-blob://cameldev?operation=getChangeFeed")
                        .to(resultName);
            }
        };
    }
}
