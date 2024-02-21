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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.Producer;
import org.apache.camel.component.azure.storage.blob.client.BlobClientFactory;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.azure.storage.blob.CredentialType.SHARED_KEY_CREDENTIAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlobComponentTest extends CamelTestSupport {

    @Test
    void testCreateEndpointWithMinConfigForClientOnly() {
        final BlobConfiguration configuration = new BlobConfiguration();
        configuration.setCredentials(storageSharedKeyCredential());
        configuration.setCredentialType(SHARED_KEY_CREDENTIAL);
        final BlobServiceClient serviceClient = BlobClientFactory.createBlobServiceClient(configuration);

        context.getRegistry().bind("azureBlobClient", serviceClient);

        final BlobEndpoint endpoint = (BlobEndpoint) context
                .getEndpoint("azure-storage-blob://camelazure/container?blobName=blob&serviceClient=#azureBlobClient");

        doTestCreateEndpointWithMinConfig(endpoint, true);
    }

    @Test
    void testCreateEndpointWithMinConfigForCredsOnly() {
        context.getRegistry().bind("creds", storageSharedKeyCredential());

        final BlobEndpoint endpoint = (BlobEndpoint) context
                .getEndpoint(
                        "azure-storage-blob://camelazure/container?blobName=blob&credentials=#creds&credentialType=SHARED_KEY_CREDENTIAL");

        doTestCreateEndpointWithMinConfig(endpoint, false);
    }

    @Test
    void testCreateEndpointWithSasToken() {

        final BlobEndpoint endpoint = (BlobEndpoint) context
                .getEndpoint(
                        "azure-storage-blob://camelazure/container?blobName=blob&credentialType=AZURE_SAS&sasToken=blabla");

        doTestCreateEndpointWithSasConfig(endpoint);
    }

    private void doTestCreateEndpointWithMinConfig(BlobEndpoint endpoint, boolean clientExpected) {
        assertEquals("camelazure", endpoint.getConfiguration().getAccountName());
        assertEquals("container", endpoint.getConfiguration().getContainerName());
        assertEquals("blob", endpoint.getConfiguration().getBlobName());
        if (clientExpected) {
            assertNotNull(endpoint.getConfiguration().getServiceClient());
            assertNull(endpoint.getConfiguration().getCredentials());
        } else {
            assertNull(endpoint.getConfiguration().getServiceClient());
            assertNotNull(endpoint.getConfiguration().getCredentials());
        }

        assertEquals(BlobType.blockblob, endpoint.getConfiguration().getBlobType());
        assertNull(endpoint.getConfiguration().getFileDir());
        assertEquals(Long.valueOf(0L), endpoint.getConfiguration().getBlobOffset());
        assertEquals(BlobOperationsDefinition.listBlobContainers, endpoint.getConfiguration().getOperation());
        assertTrue(endpoint.getConfiguration().isCloseStreamAfterRead());
        assertTrue(endpoint.getConfiguration().isCloseStreamAfterWrite());
    }

    private void doTestCreateEndpointWithSasConfig(BlobEndpoint endpoint) {
        assertEquals("camelazure", endpoint.getConfiguration().getAccountName());
        assertEquals("container", endpoint.getConfiguration().getContainerName());
        assertEquals("blob", endpoint.getConfiguration().getBlobName());
        assertEquals("blabla", endpoint.getConfiguration().getSasToken());
        assertEquals(CredentialType.AZURE_SAS, endpoint.getConfiguration().getCredentialType());
        assertNull(endpoint.getConfiguration().getServiceClient());
        assertNull(endpoint.getConfiguration().getCredentials());
        assertEquals(BlobType.blockblob, endpoint.getConfiguration().getBlobType());
        assertNull(endpoint.getConfiguration().getFileDir());
        assertEquals(Long.valueOf(0L), endpoint.getConfiguration().getBlobOffset());
        assertEquals(BlobOperationsDefinition.listBlobContainers, endpoint.getConfiguration().getOperation());
        assertTrue(endpoint.getConfiguration().isCloseStreamAfterRead());
        assertTrue(endpoint.getConfiguration().isCloseStreamAfterWrite());
    }

    @Test
    void testCreateEndpointWithMaxConfig() {
        context.getRegistry().bind("creds", storageSharedKeyCredential());
        context.getRegistry().bind("metadata", Collections.emptyMap());

        final String uri = "azure-storage-blob://camelazure/container"
                           + "?blobName=blob&credentials=#creds&credentialType=SHARED_KEY_CREDENTIAL&blobType=pageblob"
                           + "&fileDir=/tmp&blobOffset=512&operation=clearPageBlob&dataCount=1024"
                           + "&closeStreamAfterRead=false&closeStreamAfterWrite=false";
        final BlobEndpoint endpoint = (BlobEndpoint) context.getEndpoint(uri);

        assertEquals("camelazure", endpoint.getConfiguration().getAccountName());
        assertEquals("container", endpoint.getConfiguration().getContainerName());
        assertEquals("blob", endpoint.getConfiguration().getBlobName());
        assertNull(endpoint.getConfiguration().getServiceClient());
        assertNotNull(endpoint.getConfiguration().getCredentials());

        assertEquals(BlobType.pageblob, endpoint.getConfiguration().getBlobType());
        assertEquals("/tmp", endpoint.getConfiguration().getFileDir());
        assertEquals(Long.valueOf(512L), endpoint.getConfiguration().getBlobOffset());
        assertEquals(Long.valueOf(1024L), endpoint.getConfiguration().getDataCount());
        assertEquals(BlobOperationsDefinition.clearPageBlob, endpoint.getConfiguration().getOperation());
        assertFalse(endpoint.getConfiguration().isCloseStreamAfterRead());
        assertFalse(endpoint.getConfiguration().isCloseStreamAfterWrite());
    }

    @Test
    void testNoBlobNameProducerWithOpThatNeedsBlobName() {
        context.getRegistry().bind("creds", storageSharedKeyCredential());

        BlobEndpoint endpointWithOp = (BlobEndpoint) context.getEndpoint(
                "azure-storage-blob://camelazure/container?operation=deleteBlob&credentials=#creds&credentialType=SHARED_KEY_CREDENTIAL");

        Producer producer = endpointWithOp.createProducer();
        DefaultExchange exchange = new DefaultExchange(context);

        assertThrows(IllegalArgumentException.class, () -> producer.process(exchange));
    }

    @Test
    void testHierarchicalBlobName() {
        context.getRegistry().bind("creds", storageSharedKeyCredential());

        BlobEndpoint endpoint = (BlobEndpoint) context
                .getEndpoint(
                        "azure-storage-blob://camelazure/container?blobName=blob/sub&credentials=#creds&credentialType=SHARED_KEY_CREDENTIAL");
        assertEquals("blob/sub", endpoint.getConfiguration().getBlobName());
    }

    @Test
    void testCreateEndpointWithChangeFeedConfig() {
        context.getRegistry().bind("creds", storageSharedKeyCredential());
        context.getRegistry().bind("metadata", Collections.emptyMap());
        context.getRegistry().bind("starttime",
                OffsetDateTime.of(LocalDate.of(2021, 8, 4), LocalTime.of(11, 5), ZoneOffset.ofHours(0)));
        context.getRegistry().bind("endtime",
                OffsetDateTime.of(LocalDate.of(2021, 12, 4), LocalTime.of(11, 5), ZoneOffset.ofHours(0)));

        final String uri = "azure-storage-blob://camelazure"
                           + "?credentials=#creds"
                           + "&credentialType=SHARED_KEY_CREDENTIAL"
                           + "&operation=getChangeFeed"
                           + "&changeFeedStartTime=#starttime"
                           + "&changeFeedEndTime=#endtime";
        final BlobEndpoint endpoint = (BlobEndpoint) context.getEndpoint(uri);

        assertEquals("camelazure", endpoint.getConfiguration().getAccountName());
        assertNull(endpoint.getConfiguration().getServiceClient());
        assertNotNull(endpoint.getConfiguration().getCredentials());

        assertEquals(BlobOperationsDefinition.getChangeFeed, endpoint.getConfiguration().getOperation());
        assertEquals(OffsetDateTime.parse("2021-08-04T11:05Z"), endpoint.getConfiguration().getChangeFeedStartTime());
        assertEquals(OffsetDateTime.parse("2021-12-04T11:05Z"), endpoint.getConfiguration().getChangeFeedEndTime());
    }

    private StorageSharedKeyCredential storageSharedKeyCredential() {
        return new StorageSharedKeyCredential("fakeuser", "fakekey");
    }

}
