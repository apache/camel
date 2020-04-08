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

import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.queue.CloudQueue;
import org.apache.camel.component.azure.blob.BlobServiceConfiguration;
import org.apache.camel.component.azure.blob.BlobServiceEndpoint;
import org.apache.camel.component.azure.blob.BlobServiceUtil;
import org.apache.camel.component.azure.queue.QueueServiceConfiguration;
import org.apache.camel.component.azure.queue.QueueServiceEndpoint;
import org.apache.camel.component.azure.queue.QueueServiceUtil;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.ACCOUNT_NAME;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.BLOB_NAME;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.CLIENT_CREDENTIALS_ACCOUNT_KEY;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.CLIENT_CREDENTIALS_ACCOUNT_NAME;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.CONTAINER_NAME;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.CREDENTIALS_ACCOUNT_KEY;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.CREDENTIALS_ACCOUNT_NAME;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.INLINE_CREDENTIALS_ACCOUNT_KEY;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.INLINE_CREDENTIALS_ACCOUNT_NAME;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.QUEUE_NAME;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.registerBlockBlobClient;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.registerCredentials;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.registerQueueClient;

public class AzureCredentialsTest extends CamelTestSupport {

    // URI with credentials

    private String inlineCredentialBlobURIEndpoint = new StringBuilder()
            .append("azure-blob://")
            .append(ACCOUNT_NAME)
            .append("/").append(CONTAINER_NAME)
            .append("/").append(BLOB_NAME)
            .append("?")
            .append("credentialsAccountName=").append(INLINE_CREDENTIALS_ACCOUNT_NAME)
            .append("&credentialsAccountKey=RAW(").append(INLINE_CREDENTIALS_ACCOUNT_KEY).append(")")
            .toString();

    private String inlineCredentialBlobURIAndCredentialRefEndpoint = new StringBuilder()
            .append("azure-blob://")
            .append(ACCOUNT_NAME)
            .append("/").append(CONTAINER_NAME)
            .append("/").append(BLOB_NAME)
            .append("?")
            .append("credentialsAccountName=").append(INLINE_CREDENTIALS_ACCOUNT_NAME)
            .append("&credentialsAccountKey=RAW(").append(INLINE_CREDENTIALS_ACCOUNT_KEY).append(")")
            .append("&credentials=#creds")
            .toString();

    private String inlineCredentialBlobURIAndCredentialRefEndpointAndAzureClient = new StringBuilder()
            .append("azure-blob://")
            .append(ACCOUNT_NAME)
            .append("/").append(CONTAINER_NAME)
            .append("/").append(BLOB_NAME)
            .append("?")
            .append("credentialsAccountName=").append(INLINE_CREDENTIALS_ACCOUNT_NAME)
            .append("&credentialsAccountKey=RAW(").append(INLINE_CREDENTIALS_ACCOUNT_KEY).append(")")
            .append("&credentials=#creds")
            .append("&azureBlobClient=#blobClient")
            .toString();

    private String inlineCredentialQueueURIEndpoint = new StringBuilder()
            .append("azure-queue://")
            .append(ACCOUNT_NAME)
            .append("/").append(QUEUE_NAME)
            .append("?")
            .append("credentialsAccountName=").append(INLINE_CREDENTIALS_ACCOUNT_NAME)
            .append("&credentialsAccountKey=RAW(").append(INLINE_CREDENTIALS_ACCOUNT_KEY).append(")")
            .toString();

    private String inlineCredentialQueueURIAndCredentialRefEndpoint = new StringBuilder()
            .append("azure-queue://")
            .append(ACCOUNT_NAME)
            .append("/").append(QUEUE_NAME).append("?")
            .append("credentialsAccountName=").append(INLINE_CREDENTIALS_ACCOUNT_NAME)
            .append("&credentialsAccountKey=RAW(").append(INLINE_CREDENTIALS_ACCOUNT_KEY).append(")")
            .append("&credentials=#creds")
            .toString();

    private String inlineCredentialQueueURIAndCredentialRefEndpointAndAzureClient = new StringBuilder()
            .append("azure-queue://")
            .append(ACCOUNT_NAME)
            .append("/").append(QUEUE_NAME).append("?")
            .append("credentialsAccountName=").append(INLINE_CREDENTIALS_ACCOUNT_NAME)
            .append("&credentialsAccountKey=RAW(").append(INLINE_CREDENTIALS_ACCOUNT_KEY).append(")")
            .append("&credentials=#creds")
            .append("&azureQueueClient=#queueClient")
            .toString();

    // Tests

    //Blob Tests

    @Test
    public void createBlobEndpointWithAccountCredentials() throws Exception {
        executeBlobAssertions(inlineCredentialBlobURIEndpoint, INLINE_CREDENTIALS_ACCOUNT_NAME, INLINE_CREDENTIALS_ACCOUNT_KEY);
    }

    @Test
    public void createBlobEndpointWithAccountCredentialsAndCredentialsRef() throws Exception {
        registerCredentials(context, CREDENTIALS_ACCOUNT_NAME, CREDENTIALS_ACCOUNT_KEY);
        executeBlobAssertions(inlineCredentialBlobURIAndCredentialRefEndpoint, CREDENTIALS_ACCOUNT_NAME, CREDENTIALS_ACCOUNT_KEY);
    }

    @Test
    public void createBlobEndpointWithAccountCredentialsAndCredentialsRefAndAzureClient() throws Exception {
        registerCredentials(context, CREDENTIALS_ACCOUNT_NAME, CREDENTIALS_ACCOUNT_KEY);
        registerBlockBlobClient(context, CLIENT_CREDENTIALS_ACCOUNT_NAME, CLIENT_CREDENTIALS_ACCOUNT_KEY);

        BlobServiceEndpoint endpoint = (BlobServiceEndpoint)
                context.getEndpoint(inlineCredentialBlobURIAndCredentialRefEndpointAndAzureClient);
        CloudBlob client = context.getRegistry().lookupByNameAndType("blobClient", CloudBlockBlob.class);
        executeBlobAccountCredentialsAssertion(client, endpoint.getConfiguration());
        executeBlobCredentialsAssertion(client, CLIENT_CREDENTIALS_ACCOUNT_NAME, CLIENT_CREDENTIALS_ACCOUNT_KEY);
    }

    // Queue Tests

    @Test
    public void createQueueEndpointWithAccountCredentials() throws Exception {
        executeQueueAssertions(inlineCredentialQueueURIEndpoint, INLINE_CREDENTIALS_ACCOUNT_NAME, INLINE_CREDENTIALS_ACCOUNT_KEY);
    }

    @Test
    public void createQueueEndpointWithAccountCredentialsAndCredentialsRef() throws Exception {
        registerCredentials(context, CREDENTIALS_ACCOUNT_NAME, CREDENTIALS_ACCOUNT_KEY);
        executeQueueAssertions(inlineCredentialQueueURIAndCredentialRefEndpoint, CREDENTIALS_ACCOUNT_NAME, CREDENTIALS_ACCOUNT_KEY);
    }

    @Test
    public void createQueueEndpointWithAccountCredentialsAndCredentialsRefAndAzureClient() throws Exception {
        registerCredentials(context, CREDENTIALS_ACCOUNT_NAME, CREDENTIALS_ACCOUNT_KEY);
        registerQueueClient(context, CLIENT_CREDENTIALS_ACCOUNT_NAME, CLIENT_CREDENTIALS_ACCOUNT_KEY);

        QueueServiceEndpoint endpoint = (QueueServiceEndpoint) context.getEndpoint(inlineCredentialQueueURIAndCredentialRefEndpointAndAzureClient);
        CloudQueue client = context.getRegistry().lookupByNameAndType("queueClient", CloudQueue.class);
        executeQueueAccountCredentialsAssertion(client, endpoint.getConfiguration());
        executeQueueCredentialsAssertion(client, CLIENT_CREDENTIALS_ACCOUNT_NAME, CLIENT_CREDENTIALS_ACCOUNT_KEY);
    }
    

    private void executeQueueAssertions(String uriString, String expectedAccountName, String  expectedAccountKey) throws Exception {
        QueueServiceEndpoint endpoint = (QueueServiceEndpoint) context.getEndpoint(uriString);
        CloudQueue queueClient = QueueServiceUtil.createQueueClient(endpoint.getConfiguration());
        executeQueueAccountCredentialsAssertion(queueClient, endpoint.getConfiguration());
        executeQueueCredentialsAssertion(queueClient, expectedAccountName, expectedAccountKey);
    }

    private void executeQueueAccountCredentialsAssertion(CloudQueue client, QueueServiceConfiguration configuration) {
        assertNotNull(client);
        assertEquals(ACCOUNT_NAME, configuration.getAccountName());
        assertEquals(INLINE_CREDENTIALS_ACCOUNT_NAME, configuration.getCredentialsAccountName());
        assertEquals(INLINE_CREDENTIALS_ACCOUNT_KEY, configuration.getCredentialsAccountKey());
    }

    private void executeQueueCredentialsAssertion(CloudQueue client, String expectedAccountName, String expectedAccountKey) {
        StorageCredentialsAccountAndKey credentials =
                (StorageCredentialsAccountAndKey) client.getServiceClient().getCredentials();

        assertNotNull(client.getServiceClient().getCredentials());
        assertEquals(expectedAccountName, credentials.getAccountName());
        assertEquals(expectedAccountKey, credentials.exportBase64EncodedKey());
    }

    private void executeBlobAssertions(String uriString, String expectedAccountName, String  expectedAccountKey) throws Exception {
        BlobServiceEndpoint endpoint = (BlobServiceEndpoint) context.getEndpoint(uriString);

        CloudBlob pageBlobClient = BlobServiceUtil.createPageBlobClient(endpoint.createExchange(), endpoint.getConfiguration());
        executeBlobAccountCredentialsAssertion(pageBlobClient, endpoint.getConfiguration());
        executeBlobCredentialsAssertion(pageBlobClient, expectedAccountName, expectedAccountKey);

        CloudBlob blockBlobClient = BlobServiceUtil.createBlockBlobClient(endpoint.createExchange(), endpoint.getConfiguration());
        executeBlobAccountCredentialsAssertion(blockBlobClient, endpoint.getConfiguration());
        executeBlobCredentialsAssertion(blockBlobClient, expectedAccountName, expectedAccountKey);

        CloudBlob appendBlobClient = BlobServiceUtil.createAppendBlobClient(endpoint.createExchange(), endpoint.getConfiguration());
        executeBlobAccountCredentialsAssertion(appendBlobClient, endpoint.getConfiguration());
        executeBlobCredentialsAssertion(appendBlobClient, expectedAccountName, expectedAccountKey);
    }

    private void executeBlobAccountCredentialsAssertion(CloudBlob client, BlobServiceConfiguration configuration) {
        assertNotNull(client);
        assertEquals(ACCOUNT_NAME, configuration.getAccountName());
        assertEquals(CONTAINER_NAME, configuration.getContainerName());
        assertEquals(BLOB_NAME, configuration.getBlobName());
        assertEquals(INLINE_CREDENTIALS_ACCOUNT_NAME, configuration.getCredentialsAccountName());
        assertEquals(INLINE_CREDENTIALS_ACCOUNT_KEY, configuration.getCredentialsAccountKey());
    }

    private void executeBlobCredentialsAssertion(CloudBlob client, String expectedAccountName, String expectedAccountKey) {
        StorageCredentialsAccountAndKey credentials = (StorageCredentialsAccountAndKey)
                client.getServiceClient().getCredentials();
        assertNotNull(client.getServiceClient().getCredentials());
        assertEquals(expectedAccountName, credentials.getAccountName());
        assertEquals(expectedAccountKey, credentials.exportBase64EncodedKey());
    }
}
