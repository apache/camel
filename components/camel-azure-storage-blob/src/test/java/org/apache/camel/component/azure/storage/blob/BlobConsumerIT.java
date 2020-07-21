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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Properties;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.specialized.BlobInputStream;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.storage.blob.client.BlobClientFactory;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BlobConsumerIT extends CamelTestSupport {

    @TempDir
    static Path testDir;
    @EndpointInject("direct:start")
    private ProducerTemplate templateStart;
    private String containerName;
    private String blobName;
    private String blobName2;

    private BlobContainerClient containerClient;

    @BeforeAll
    public void prepare() throws Exception {
        containerName = RandomStringUtils.randomAlphabetic(5).toLowerCase();
        blobName = RandomStringUtils.randomAlphabetic(5);
        blobName2 = RandomStringUtils.randomAlphabetic(5);

        BlobConfiguration configuration = new BlobConfiguration();
        configuration.setCredentials(storageSharedKeyCredential());
        configuration.setContainerName(containerName);
        configuration.setBlobName(blobName);

        final BlobServiceClient serviceClient = BlobClientFactory.createBlobServiceClient(configuration);
        containerClient = serviceClient.getBlobContainerClient(containerName);

        // create test container
        containerClient.create();

    }

    @Test
    public void testPollingToFile() throws IOException, InterruptedException {
        templateStart.send("direct:createBlob", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setBody("Block Blob");
            exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, containerName);
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName);
        });

        final MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.assertIsSatisfied(100);

        final File file = mockEndpoint.getExchanges().get(0).getIn().getBody(File.class);
        assertNotNull(file, "File must be set");
        assertEquals("Block Blob", FileUtils.readFileToString(file, Charset.defaultCharset()));
    }

    @Test
    public void testPollingToInputStream() throws InterruptedException, IOException {
        templateStart.send("direct:createBlob", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setBody("Block Blob");
            exchange.getIn().setHeader(BlobConstants.BLOB_CONTAINER_NAME, containerName);
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName2);
        });

        final MockEndpoint mockEndpoint = getMockEndpoint("mock:resultOutputStream");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.assertIsSatisfied(10000);

        final BlobInputStream blobInputStream = mockEndpoint.getExchanges().get(0).getIn().getBody(BlobInputStream.class);
        assertNotNull(blobInputStream, "BlobInputStream must be set");

        final String bufferedText = new BufferedReader(new InputStreamReader(blobInputStream)).readLine();

        assertEquals("Block Blob", bufferedText);
    }

    @AfterAll
    public void tearDown() {
        // delete container
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
                from("direct:createBlob")
                        .to("azure-storage-blob://cameldev?credentials=#creds&operation=uploadBlockBlob");

                from("azure-storage-blob://cameldev/" + containerName + "?blobName=" + blobName + "&credentials=#creds&fileDir="
                        + testDir.toString()).to("mock:result");

                from("azure-storage-blob://cameldev/" + containerName + "?blobName=" + blobName2 + "&credentials=#creds").to("mock:resultOutputStream");
            }
        };
    }


    private StorageSharedKeyCredential storageSharedKeyCredential() throws Exception {
        final Properties properties = BlobTestUtils.loadAzureAccessFromJvmEnv();
        return new StorageSharedKeyCredential(properties.getProperty("account_name"), properties.getProperty("access_key"));
    }
}
