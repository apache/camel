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
package org.apache.camel.component.azure.blob;

import java.net.URI;

import com.microsoft.azure.storage.blob.CloudAppendBlob;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.registerCredentials;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BlobServiceUtilTest extends CamelTestSupport {

    @Test
    public void testPrepareUri() throws Exception {
        registerCredentials(context);

        BlobServiceEndpoint endpoint
                = (BlobServiceEndpoint) context.getEndpoint("azure-blob://camelazure/container/blob?credentials=#creds");
        URI uri = BlobServiceUtil.prepareStorageBlobUri(endpoint.createExchange(), endpoint.getConfiguration());
        assertEquals("https://camelazure.blob.core.windows.net/container/blob", uri.toString());
    }

    @Test
    public void testGetConfiguredClient() throws Exception {
        CloudBlockBlob client = new CloudBlockBlob(URI.create("https://camelazure.blob.core.windows.net/container/blob"));

        context.getRegistry().bind("azureBlobClient", client);

        BlobServiceEndpoint endpoint = (BlobServiceEndpoint) context
                .getEndpoint("azure-blob://camelazure/container/blob?azureBlobClient=#azureBlobClient&publicForRead=true");
        assertSame(client, BlobServiceUtil.getConfiguredClient(endpoint.createExchange(), endpoint.getConfiguration()));
    }

    @Test
    public void testGetConfiguredClientTypeMismatch() throws Exception {
        CloudBlockBlob client = new CloudBlockBlob(URI.create("https://camelazure.blob.core.windows.net/container/blob"));

        context.getRegistry().bind("azureBlobClient", client);

        BlobServiceEndpoint endpoint = (BlobServiceEndpoint) context
                .getEndpoint("azure-blob://camelazure/container/blob?azureBlobClient=#azureBlobClient&publicForRead=true"
                             + "&blobType=appendBlob");

        Exchange exchange = endpoint.createExchange();
        BlobServiceConfiguration configuration = endpoint.getConfiguration();

        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> BlobServiceUtil.getConfiguredClient(exchange, configuration));

        assertEquals("Invalid Client Type", ex.getMessage());
    }

    @Test
    public void testGetConfiguredClientUriMismatch() throws Exception {
        CloudAppendBlob client = new CloudAppendBlob(URI.create("https://camelazure.blob.core.windows.net/container/blob"));

        context.getRegistry().bind("azureBlobClient", client);

        BlobServiceEndpoint endpoint = (BlobServiceEndpoint) context
                .getEndpoint("azure-blob://camelazure/container/blob2?azureBlobClient=#azureBlobClient&publicForRead=true"
                             + "&blobType=appendBlob");

        Exchange exchange = endpoint.createExchange();
        BlobServiceConfiguration configuration = endpoint.getConfiguration();

        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> BlobServiceUtil.getConfiguredClient(exchange, configuration));

        assertEquals("Invalid Client URI", ex.getMessage());
    }

    @Test
    public void testGetConfiguredClientWithClientUriValidationDisabled() throws Exception {
        URI uri = URI.create("https://custom/azure/service/url/container/blob");
        CloudAppendBlob client = new CloudAppendBlob(uri);

        context.getRegistry().bind("azureBlobClient", client);

        BlobServiceEndpoint endpoint = (BlobServiceEndpoint) context
                .getEndpoint("azure-blob://camelazure/container/blob?azureBlobClient=#azureBlobClient&publicForRead=true"
                             + "&blobType=appendBlob&validateClientURI=false");

        CloudBlob configuredClient
                = BlobServiceUtil.getConfiguredClient(endpoint.createExchange(), endpoint.getConfiguration());
        assertEquals(uri, configuredClient.getUri());
    }
}
