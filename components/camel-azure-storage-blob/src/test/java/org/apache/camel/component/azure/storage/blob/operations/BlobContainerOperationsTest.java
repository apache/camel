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
package org.apache.camel.component.azure.storage.blob.operations;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import com.azure.core.http.HttpHeaders;
import com.azure.storage.blob.models.BlobItem;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.client.BlobContainerClientWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
class BlobContainerOperationsTest {

    private BlobConfiguration configuration;

    @Mock
    private BlobContainerClientWrapper client;

    @BeforeEach
    public void setup() {
        configuration = new BlobConfiguration();
        configuration.setAccountName("cameldev");
        configuration.setContainerName("awesome2");
    }

    @Test
    public void testCreateContainer() {
        when(client.createContainer(any(), any(), any())).thenReturn(createContainerMock());

        final BlobContainerOperations blobContainerOperations = new BlobContainerOperations(client);
        final BlobOperationResponse response = blobContainerOperations.createContainer(null);

        assertNotNull(response);
        assertNotNull(response.getHeaders().get(BlobConstants.RAW_HTTP_HEADERS));
        assertTrue((boolean) response.getBody());
    }

    @Test
    public void testDeleteContainer() {
        when(client.deleteContainer(any(), any())).thenReturn(deleteContainerMock());

        final BlobContainerOperations blobContainerOperations = new BlobContainerOperations(client);
        final BlobOperationResponse response = blobContainerOperations.deleteContainer(null);

        assertNotNull(response);
        assertNotNull(response.getHeaders().get(BlobConstants.RAW_HTTP_HEADERS));
        assertTrue((boolean) response.getBody());
    }

    @Test
    public void testListBlob() {
        when(client.listBlobs(any(), any())).thenReturn(listBlobsMock());

        final BlobContainerOperations blobContainerOperations = new BlobContainerOperations(client);
        final BlobOperationResponse response = blobContainerOperations.listBlobs(null);

        assertNotNull(response);

        @SuppressWarnings("unchecked") final List<String> items = ((List<BlobItem>) response.getBody()).stream().map(BlobItem::getName).collect(Collectors.toList());

        assertTrue(items.contains("item-1"));
        assertTrue(items.contains("item-2"));
    }

    private HttpHeaders createContainerMock() {
        final HttpHeaders httpHeaders = new HttpHeaders();

        httpHeaders.put("x-ms-request-id", "12345");
        httpHeaders.put("Server", "Azure-Server");

        return httpHeaders;
    }

    private HttpHeaders deleteContainerMock() {
        final HttpHeaders httpHeaders = new HttpHeaders();

        httpHeaders.put("x-ms-request-id", "12345");
        httpHeaders.put("Server", "Azure-Server");
        httpHeaders.put("x-ms-delete-type-permanent", "true");

        return httpHeaders;
    }

    private List<BlobItem> listBlobsMock() {
        final List<BlobItem> items = new LinkedList<>();

        final BlobItem blobItem1 = new BlobItem().setName("item-1").setVersionId("1").setDeleted(false);
        final BlobItem blobItem2 = new BlobItem().setName("item-2").setVersionId("2").setDeleted(true);

        items.add(blobItem1);
        items.add(blobItem2);

        return items;
    }

}