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

import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class BlobConsumerTest extends CamelTestSupport {

    @Test
    void testMoveAfterReadWithoutDestinationContainerShouldFail() throws Exception {
        context.getRegistry().bind("creds", storageSharedKeyCredential());

        final BlobEndpoint endpoint = (BlobEndpoint) context
                .getEndpoint(
                        "azure-storage-blob://camelazure/container?blobName=blob&credentials=#creds&credentialType=SHARED_KEY_CREDENTIAL"
                             + "&moveAfterRead=true");

        BlobConsumer consumer = (BlobConsumer) endpoint.createConsumer(exchange -> {
        });

        // Should throw IllegalArgumentException because destinationContainer is not set
        assertThrows(IllegalArgumentException.class, consumer::start);
    }

    @Test
    void testMoveAfterReadWithDestinationContainerShouldNotFail() throws Exception {
        context.getRegistry().bind("creds", storageSharedKeyCredential());

        final BlobEndpoint endpoint = (BlobEndpoint) context
                .getEndpoint(
                        "azure-storage-blob://camelazure/container?blobName=blob&credentials=#creds&credentialType=SHARED_KEY_CREDENTIAL"
                             + "&moveAfterRead=true&destinationContainer=archive");

        BlobConsumer consumer = (BlobConsumer) endpoint.createConsumer(exchange -> {
        });

        // Should not throw - the consumer should start successfully
        // We can't fully test the move functionality without a real Azure connection,
        // but at least validation should pass
        try {
            consumer.start();
        } catch (Exception e) {
            // Expected to fail later when trying to connect to Azure, but not on validation
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
        } finally {
            try {
                consumer.stop();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void testDeleteAfterReadConfigured() throws Exception {
        context.getRegistry().bind("creds", storageSharedKeyCredential());

        final BlobEndpoint endpoint = (BlobEndpoint) context
                .getEndpoint(
                        "azure-storage-blob://camelazure/container?blobName=blob&credentials=#creds&credentialType=SHARED_KEY_CREDENTIAL"
                             + "&deleteAfterRead=true");

        BlobConsumer consumer = (BlobConsumer) endpoint.createConsumer(exchange -> {
        });

        // Should start without validation errors - deleteAfterRead doesn't require extra config
        try {
            consumer.start();
        } catch (Exception e) {
            // Expected to fail later when trying to connect to Azure, but not on validation
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
        } finally {
            try {
                consumer.stop();
            } catch (Exception ignored) {
            }
        }
    }

    private StorageSharedKeyCredential storageSharedKeyCredential() {
        return new StorageSharedKeyCredential("fakeuser", "fakekey");
    }
}
