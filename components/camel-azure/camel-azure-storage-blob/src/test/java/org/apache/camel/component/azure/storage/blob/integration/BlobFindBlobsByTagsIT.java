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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.azure.storage.blob.models.TaggedBlobItem;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.BlobUtils;
import org.apache.camel.component.azure.storage.blob.client.BlobClientWrapper;
import org.apache.camel.component.azure.storage.blob.client.BlobContainerClientWrapper;
import org.apache.camel.component.azure.storage.blob.client.BlobServiceClientWrapper;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperationResponse;
import org.apache.camel.component.azure.storage.blob.operations.BlobServiceOperations;
import org.apache.camel.support.DefaultExchange;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlobFindBlobsByTagsIT extends Base {

    private BlobContainerClientWrapper blobContainerClientWrapper;
    private BlobServiceClientWrapper blobServiceClientWrapper;
    private String randomBlobName;

    @BeforeAll
    public void setup() throws Exception {
        randomBlobName = RandomStringUtils.randomAlphabetic(10);

        blobServiceClientWrapper = new BlobServiceClientWrapper(serviceClient);
        blobContainerClientWrapper = blobServiceClientWrapper.getBlobContainerClientWrapper(configuration.getContainerName());

        blobContainerClientWrapper.createContainer(null, null, null);

        // create a blob and tag it so the filter has something to match
        final InputStream inputStream = new ByteArrayInputStream("findBlobsByTags content".getBytes());
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper(randomBlobName);
        blobClientWrapper.uploadBlockBlob(inputStream, BlobUtils.getInputStreamLength(inputStream), null, null, null, null,
                null, null);

        final Map<String, String> tags = new HashMap<>();
        tags.put("Environment", "Production");
        tags.put("Status", "Active");
        blobClientWrapper.setTags(tags, null, null);
    }

    @AfterAll
    public void cleanup() {
        blobContainerClientWrapper.deleteContainer(null, null);
    }

    @Test
    void testFindBlobsByTagsReturnsList() {
        final BlobServiceOperations operations = new BlobServiceOperations(configuration, blobServiceClientWrapper);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(BlobConstants.BLOB_TAG_FILTER, "\"Environment\" = 'Production'");

        final BlobOperationResponse response = operations.findBlobsByTags(exchange);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertInstanceOfList(response.getBody());
    }

    @Test
    void testFindBlobsByTagsThrowsWithoutFilter() {
        final BlobServiceOperations operations = new BlobServiceOperations(configuration, blobServiceClientWrapper);

        final Exchange exchange = new DefaultExchange(context);
        assertThrows(IllegalArgumentException.class, () -> operations.findBlobsByTags(exchange));
    }

    private static void assertInstanceOfList(final Object body) {
        assertTrue(body instanceof List, "Expected response body to be a List but was " + body.getClass());
        for (Object item : (List<?>) body) {
            assertTrue(item instanceof TaggedBlobItem,
                    "Expected list element to be a TaggedBlobItem but was " + item.getClass());
        }
    }
}
