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

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.PublicAccessType;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.client.BlobContainerClientWrapper;
import org.apache.camel.component.azure.storage.blob.client.BlobServiceClientWrapper;
import org.apache.camel.component.azure.storage.blob.operations.BlobContainerOperations;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperationResponse;
import org.apache.camel.support.DefaultExchange;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlobContainerOperationsIT extends Base {

    private BlobServiceClientWrapper blobServiceClientWrapper;

    @BeforeAll
    public void setup() {
        blobServiceClientWrapper = new BlobServiceClientWrapper(serviceClient);
    }

    @Test
    void testCreateAndDeleteContainer() {
        final BlobContainerClientWrapper containerClientWrapper
                = blobServiceClientWrapper.getBlobContainerClientWrapper("testcontainer1");
        final BlobContainerOperations blobContainerOperations
                = new BlobContainerOperations(configuration, containerClientWrapper);

        final BlobOperationResponse response = blobContainerOperations.createContainer(null);

        assertNotNull(response);
        assertNotNull(response.getHeaders().get(BlobConstants.RAW_HTTP_HEADERS));
        assertTrue((boolean) response.getBody());

        // delete everything
        blobContainerOperations.deleteContainer(null);

        // test with options being set
        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(BlobConstants.METADATA, Collections.singletonMap("testKeyMetadata", "testValueMetadata"));
        exchange.getIn().setHeader(BlobConstants.PUBLIC_ACCESS_TYPE, PublicAccessType.CONTAINER);

        // try to create the container again, we try until we can
        Awaitility.given()
                .ignoreException(BlobStorageException.class)
                .with()
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .await()
                .atMost(60, TimeUnit.SECONDS)
                .until(() -> {
                    final BlobOperationResponse response1 = blobContainerOperations.createContainer(exchange);
                    assertNotNull(response1);
                    assertNotNull(response1.getHeaders().get(BlobConstants.RAW_HTTP_HEADERS));

                    return (boolean) response1.getBody();
                });
    }

    @AfterAll
    public void cleanUp() {
        blobServiceClientWrapper.getBlobContainerClientWrapper("testcontainer1").deleteContainer(null, null);
    }
}
