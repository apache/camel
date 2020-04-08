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
package org.apache.camel.component.azure.common;

import java.net.URI;

import com.microsoft.azure.storage.StorageCredentialsAnonymous;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.azure.blob.BlobServiceEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.apache.camel.component.azure.blob.BlobServiceComponent.MISSING_BLOB_CREDENTIALS_EXCEPTION_MESSAGE;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.ACCOUNT_NAME;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.BLOB_NAME;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.CONTAINER_NAME;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.INLINE_CREDENTIALS_ACCOUNT_KEY;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.INLINE_CREDENTIALS_ACCOUNT_NAME;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.QUEUE_NAME;
import static org.apache.camel.component.azure.queue.QueueServiceComponent.MISSING_QUEUE_CREDNTIALS_EXCEPTION_MESSAGE;

public class MissingCredentialsTest extends CamelTestSupport {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    //Missing credentials
    private String missingCredentialsBlobUriEndoint = new StringBuilder()
            .append("azure-blob://")
            .append(ACCOUNT_NAME)
            .append("/").append(CONTAINER_NAME)
            .append("/").append(BLOB_NAME)
            .toString();

    private String missingCredentialsAccountNameBlobUriEndoint = new StringBuilder()
            .append("azure-blob://")
            .append(ACCOUNT_NAME)
            .append("/").append(CONTAINER_NAME)
            .append("/").append(BLOB_NAME)
            .append("?")
            .append("credentialsAccountKey=RAW(").append(INLINE_CREDENTIALS_ACCOUNT_KEY).append(")")
            .toString();

    private String missingCredentialsAccountKeyBlobUriEndoint = new StringBuilder()
            .append("azure-blob://")
            .append(ACCOUNT_NAME)
            .append("/").append(CONTAINER_NAME)
            .append("/").append(BLOB_NAME)
            .append("?")
            .append("credentialsAccountName=").append(INLINE_CREDENTIALS_ACCOUNT_NAME)
            .toString();

    private String missingCredentialsQueueUriEndoint = new StringBuilder()
            .append("azure-queue://")
            .append(ACCOUNT_NAME)
            .append("/").append(QUEUE_NAME)
            .toString();

    private String missingCredentialsAccountNameQueueUriEndoint = new StringBuilder()
            .append("azure-queue://")
            .append(ACCOUNT_NAME)
            .append("/").append(QUEUE_NAME)
            .append("?")
            .append("credentialsAccountKey=RAW(").append(INLINE_CREDENTIALS_ACCOUNT_KEY).append(")")
            .toString();

    private String missingCredentialsAccountKeyQueueUriEndoint = new StringBuilder()
            .append("azure-queue://")
            .append(ACCOUNT_NAME)
            .append("/").append(QUEUE_NAME)
            .append("?")
            .append("credentialsAccountName=").append(INLINE_CREDENTIALS_ACCOUNT_NAME)
            .toString();


    // Missing Credentials Blob Tests
    @Test
    public void createBlobEndpointWithoutCredentials() {
        createEndpointWithoutCredentials(missingCredentialsBlobUriEndoint, MISSING_BLOB_CREDENTIALS_EXCEPTION_MESSAGE);
    }

    @Test
    public void testNoClientAndCredentialsPublicForRead() throws Exception {
        BlobServiceEndpoint endpoint =
                (BlobServiceEndpoint) context.getEndpoint("azure-blob://camelazure/container/blob?publicForRead=true");
        assertTrue(endpoint.getConfiguration().isPublicForRead());
    }

    @Test
    public void createBlobEndpointWithoutCredentialsAccountName() {
        createEndpointWithoutCredentials(missingCredentialsAccountNameBlobUriEndoint, MISSING_BLOB_CREDENTIALS_EXCEPTION_MESSAGE);
    }

    @Test
    public void createBlobEndpointWithoutCredentialsAccountKey() {
        createEndpointWithoutCredentials(missingCredentialsAccountKeyBlobUriEndoint, MISSING_BLOB_CREDENTIALS_EXCEPTION_MESSAGE);
    }

    // Missing Credentials Queue Tests
    @Test
    public void createQueueEndpointWithoutCredentials() {
        createEndpointWithoutCredentials(missingCredentialsQueueUriEndoint, MISSING_QUEUE_CREDNTIALS_EXCEPTION_MESSAGE);
    }

    @Test
    public void createQueueEndpointWithoutCredentialsAccountName() {
        createEndpointWithoutCredentials(missingCredentialsAccountNameQueueUriEndoint, MISSING_QUEUE_CREDNTIALS_EXCEPTION_MESSAGE);
    }

    @Test
    public void createQueueEndpointWithoutCredentialsAccountKey() {
        createEndpointWithoutCredentials(missingCredentialsAccountKeyQueueUriEndoint, MISSING_QUEUE_CREDNTIALS_EXCEPTION_MESSAGE);
    }


    // Client Tests

    @Test
    public void testBlobClientWithoutCredentialsPublicRead() throws Exception {
        CloudBlockBlob client =
                new CloudBlockBlob(URI.create("https://camelazure.blob.core.windows.net/container/blob"));

        context.getRegistry().bind("azureBlobClient", client);

        BlobServiceEndpoint endpoint =
                (BlobServiceEndpoint) context.getEndpoint("azure-blob://camelazure/container/blob?publicForRead=true");
        assertTrue(endpoint.getConfiguration().isPublicForRead());
    }

    @Test
    public void testBlobClientWithoutAnonymousCredentials() throws Exception {
        exceptionRule.expect(ResolveEndpointFailedException.class);
        exceptionRule.expectMessage(MISSING_BLOB_CREDENTIALS_EXCEPTION_MESSAGE);
        CloudBlockBlob client =
                new CloudBlockBlob(URI.create("https://camelazure.blob.core.windows.net/container/blob"),
                        StorageCredentialsAnonymous.ANONYMOUS);
        context.getRegistry().bind("azureBlobClient", client);
        context.getEndpoint("azure-blob://camelazure/container/blob");
    }

    @Test
    public void testBlobClientWithoutCredentials() throws Exception {
        exceptionRule.expect(ResolveEndpointFailedException.class);
        exceptionRule.expectMessage(MISSING_BLOB_CREDENTIALS_EXCEPTION_MESSAGE);
        CloudBlockBlob client =
                new CloudBlockBlob(URI.create("https://camelazure.blob.core.windows.net/container/blob"));
        context.getRegistry().bind("azureBlobClient", client);
        context.getEndpoint("azure-blob://camelazure/container/blob");

    }


    private void createEndpointWithoutCredentials(String uri, String errorMessage) {
        exceptionRule.expect(ResolveEndpointFailedException.class);
        exceptionRule.expectMessage(errorMessage);
        context.getEndpoint(uri);
    }
}
