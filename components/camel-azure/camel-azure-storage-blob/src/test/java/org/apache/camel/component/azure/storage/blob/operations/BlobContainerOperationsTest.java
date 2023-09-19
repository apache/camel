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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void testCreateContainer() {
        when(client.createContainer(any(), any(), any())).thenReturn(createContainerMock());

        final BlobContainerOperations blobContainerOperations = new BlobContainerOperations(configuration, client);
        final BlobOperationResponse response = blobContainerOperations.createContainer(null);

        assertNotNull(response);
        assertNotNull(response.getHeaders().get(BlobConstants.RAW_HTTP_HEADERS));
        assertTrue((boolean) response.getBody());
    }

    @Test
    void testDeleteContainer() {
        when(client.deleteContainer(any(), any())).thenReturn(deleteContainerMock());

        final BlobContainerOperations blobContainerOperations = new BlobContainerOperations(configuration, client);
        final BlobOperationResponse response = blobContainerOperations.deleteContainer(null);

        assertNotNull(response);
        assertNotNull(response.getHeaders().get(BlobConstants.RAW_HTTP_HEADERS));
        assertTrue((boolean) response.getBody());
    }

    @Test
    void testListBlob() {
        when(client.listBlobs(any(), any())).thenReturn(listBlobsMock());

        final BlobContainerOperations blobContainerOperations = new BlobContainerOperations(configuration, client);
        final BlobOperationResponse response = blobContainerOperations.listBlobs(null);

        assertNotNull(response);

        @SuppressWarnings("unchecked")
        final List<BlobItem> body = (List<BlobItem>) response.getBody();
        final List<String> items = body.stream().map(BlobItem::getName).toList();

        assertTrue(items.contains("item-1"));
        assertTrue(items.contains("item-2"));
    }

    @Test
    void testListBlobWithRegex() {

        BlobConfiguration myConfiguration = new BlobConfiguration();
        myConfiguration.setAccountName("cameldev");
        myConfiguration.setContainerName("awesome2");
        myConfiguration.setRegex(".*\\.pdf");

        when(client.listBlobs(any(), any())).thenReturn(listBlobsMockWithRegex());

        final BlobContainerOperations blobContainerOperations = new BlobContainerOperations(myConfiguration, client);
        final BlobOperationResponse response = blobContainerOperations.listBlobs(null);

        assertNotNull(response);

        @SuppressWarnings("unchecked")
        final List<BlobItem> body = (List<BlobItem>) response.getBody();
        final List<String> items = body.stream().map(BlobItem::getName).toList();
        assertEquals(3, items.size());
        assertTrue(items.contains("invoice1.pdf"));
        assertTrue(items.contains("invoice2.pdf"));
        assertTrue(items.contains("invoice5.pdf"));
    }

    private HttpHeaders createContainerMock() {
        final HttpHeaders httpHeaders = new HttpHeaders();

        httpHeaders.set("x-ms-request-id", "12345");
        httpHeaders.set("Server", "Azure-Server");

        return httpHeaders;
    }

    private HttpHeaders deleteContainerMock() {
        final HttpHeaders httpHeaders = new HttpHeaders();

        httpHeaders.set("x-ms-request-id", "12345");
        httpHeaders.set("Server", "Azure-Server");
        httpHeaders.set("x-ms-delete-type-permanent", "true");

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

    private List<BlobItem> listBlobsMockWithRegex() {
        final List<BlobItem> items = listBlobsMock();

        final BlobItem blobItemPdf1 = new BlobItem().setName("invoice1.pdf").setVersionId("1").setDeleted(false);
        final BlobItem blobItemPdf2 = new BlobItem().setName("invoice2.pdf").setVersionId("2").setDeleted(true);
        final BlobItem blobItemPdf3 = new BlobItem().setName("invoice5.pdf").setVersionId("2").setDeleted(true);

        final BlobItem blobItemExe1 = new BlobItem().setName("office.exe").setVersionId("1").setDeleted(false);
        final BlobItem blobItemExe2 = new BlobItem().setName("autorun.exe").setVersionId("2").setDeleted(false);
        final BlobItem blobItemExe3 = new BlobItem().setName("start.exe").setVersionId("2").setDeleted(true);

        items.add(blobItemPdf1);
        items.add(blobItemPdf2);
        items.add(blobItemPdf3);

        items.add(blobItemExe1);
        items.add(blobItemExe2);
        items.add(blobItemExe3);

        return items;
    }

}
