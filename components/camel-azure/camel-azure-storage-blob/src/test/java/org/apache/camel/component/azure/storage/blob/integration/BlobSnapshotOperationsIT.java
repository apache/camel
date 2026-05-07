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

import com.azure.storage.blob.models.BlobStorageException;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.client.BlobClientWrapper;
import org.apache.camel.component.azure.storage.blob.client.BlobContainerClientWrapper;
import org.apache.camel.component.azure.storage.blob.client.BlobServiceClientWrapper;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperationResponse;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperations;
import org.apache.camel.support.DefaultExchange;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlobSnapshotOperationsIT extends Base {

    private static final String BLOB_CONTENT = "snapshot retrieval content";

    private BlobContainerClientWrapper blobContainerClientWrapper;

    @BeforeAll
    public void setup() {
        blobContainerClientWrapper = new BlobServiceClientWrapper(serviceClient)
                .getBlobContainerClientWrapper(configuration.getContainerName());

        blobContainerClientWrapper.createContainer(null, null, null);
    }

    @AfterAll
    public void cleanup() {
        blobContainerClientWrapper.deleteContainer(null, null);
    }

    @Test
    void testCreateSnapshotReturnsId() throws Exception {
        final BlobOperations operations = freshBlob();

        uploadContent(operations, BLOB_CONTENT);

        final BlobOperationResponse response = operations.createBlobSnapshot(new DefaultExchange(context));
        assertNotNull(response);

        final String snapshotId = (String) response.getBody();
        assertNotNull(snapshotId);
        assertEquals(snapshotId, response.getHeaders().get(BlobConstants.BLOB_SNAPSHOT_ID));
    }

    @Test
    void testReadBlobViaSnapshotIdOnConfig() throws Exception {
        final BlobOperations operations = freshBlob();

        uploadContent(operations, BLOB_CONTENT);

        final String snapshotId = (String) operations.createBlobSnapshot(new DefaultExchange(context)).getBody();
        assertNotNull(snapshotId);

        try {
            configuration.setSnapshotId(snapshotId);
            assertEquals(BLOB_CONTENT, readBlob(operations, null));
        } finally {
            configuration.setSnapshotId(null);
        }

        // sanity: clearing the snapshotId still reads the live blob
        assertEquals(BLOB_CONTENT, readBlob(operations, null));
    }

    @Test
    void testReadBlobViaSnapshotIdOnHeader() throws Exception {
        final BlobOperations operations = freshBlob();

        uploadContent(operations, BLOB_CONTENT);

        final String snapshotId = (String) operations.createBlobSnapshot(new DefaultExchange(context)).getBody();
        assertNotNull(snapshotId);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(BlobConstants.BLOB_SNAPSHOT_ID, snapshotId);
        assertEquals(BLOB_CONTENT, readBlob(operations, exchange));
    }

    @Test
    void testReadWithNonExistentSnapshotIdFails() throws Exception {
        final BlobOperations operations = freshBlob();

        uploadContent(operations, BLOB_CONTENT);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(BlobConstants.BLOB_SNAPSHOT_ID, "2020-01-01T00:00:00.0000000Z");
        // proves the snapshotId is actually threaded into the SDK client: targeting a missing snapshot must fail
        assertThrows(BlobStorageException.class, () -> readBlob(operations, exchange));
    }

    @Test
    void testDownloadLinkContainsSnapshotQueryParam() throws Exception {
        final BlobOperations operations = freshBlob();

        uploadContent(operations, BLOB_CONTENT);

        final String snapshotId = (String) operations.createBlobSnapshot(new DefaultExchange(context)).getBody();
        assertNotNull(snapshotId);

        final String liveLink = (String) operations.downloadLink(null).getHeaders().get(BlobConstants.DOWNLOAD_LINK);
        assertNotNull(liveLink);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(BlobConstants.BLOB_SNAPSHOT_ID, snapshotId);
        final String snapshotLink = (String) operations.downloadLink(exchange).getHeaders().get(BlobConstants.DOWNLOAD_LINK);
        assertNotNull(snapshotLink);

        assertNotEquals(liveLink, snapshotLink);
        // snapshot-scoped URLs include the snapshot query parameter before the SAS token
        assertTrue(snapshotLink.contains("snapshot="));
    }

    private BlobOperations freshBlob() {
        final BlobClientWrapper clientWrapper
                = blobContainerClientWrapper.getBlobClientWrapper(RandomStringUtils.randomAlphabetic(10));
        return new BlobOperations(configuration, clientWrapper);
    }

    private void uploadContent(final BlobOperations operations, final String content) throws Exception {
        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        operations.uploadBlockBlob(exchange);
    }

    private String readBlob(final BlobOperations operations, final Exchange exchange) throws Exception {
        final BlobOperationResponse response = operations.getBlob(exchange);
        return IOUtils.toString((InputStream) response.getBody(), StandardCharsets.UTF_8);
    }
}
