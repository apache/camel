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

import com.microsoft.azure.storage.StorageCredentials;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.core.Base64;
import com.microsoft.azure.storage.queue.CloudQueue;
import org.apache.camel.CamelContext;

public final class AzureServiceCommonTestUtil {

    public static final String ACCOUNT_NAME = "camelazure";
    public static final String CONTAINER_NAME = "container1";
    public static final String BLOB_NAME = "blobBlock";
    public static final String INLINE_CREDENTIALS_ACCOUNT_NAME = "xxxx";
    public static final String INLINE_CREDENTIALS_ACCOUNT_KEY = Base64.encode("yyyy".getBytes());
    public static final String CREDENTIALS_ACCOUNT_NAME = "aaaa";
    public static final String CREDENTIALS_ACCOUNT_KEY = Base64.encode("bbbb".getBytes());
    public static final String CLIENT_CREDENTIALS_ACCOUNT_NAME = "cccc";
    public static final String CLIENT_CREDENTIALS_ACCOUNT_KEY = Base64.encode("dddd".getBytes());
    public static final String QUEUE_NAME = "myQueue";

    private static final String DEFAULT_ACCOUNT_NAME = "camelazure";
    private static final String DEFAULT_ACCOUNT_KEY = Base64.encode("camelazure".getBytes());


    private AzureServiceCommonTestUtil() { }

    // Credentials

    public static void registerCredentials(CamelContext context, String accountName, String accountKey) {
        context.getRegistry().bind("creds", newAccountKeyCredentials(accountName, accountKey));
    }

    public static void registerCredentials(CamelContext context) {
        context.getRegistry().bind("creds", newAccountKeyCredentials(DEFAULT_ACCOUNT_NAME, DEFAULT_ACCOUNT_KEY));
    }

    //  BlobClient

    public static void registerBlockBlobClient(CamelContext context) throws Exception {
        context.getRegistry().bind("blobClient", createBlockBlobClient());
    }

    public static void registerBlockBlobClient(CamelContext context, String accountName, String accountKey) throws Exception {
        context.getRegistry().bind("blobClient", createBlockBlobClient(accountName, accountKey));
    }

    public static CloudBlockBlob createBlockBlobClient(String accountName, String accountKey) throws Exception {
        URI uri = new URI("https://camelazure.blob.core.windows.net/container1/blobBlock");
        return new CloudBlockBlob(uri, newAccountKeyCredentials(accountName, accountKey));
    }

    public static CloudBlockBlob createBlockBlobClient() throws Exception {
        return createBlockBlobClient(DEFAULT_ACCOUNT_NAME, DEFAULT_ACCOUNT_KEY);
    }

    //Queue Client

    public static void registerQueueClient(CamelContext context, String accountName, String accountKey) throws Exception {
        context.getRegistry().bind("queueClient", createQueueClient(accountName, accountKey));
    }

    public static void registerQueueClient(CamelContext context) throws Exception {
        registerQueueClient(context, DEFAULT_ACCOUNT_NAME, DEFAULT_ACCOUNT_KEY);
    }

    public static CloudQueue createQueueClient(String accountName, String accountKey) throws Exception {
        URI uri = new URI("https://camelazure.queue.core.windows.net/testqueue/");
        return new CloudQueue(uri, newAccountKeyCredentials(accountName, accountKey));
    }

    public static CloudQueue createQueueClient() throws Exception {
        return createQueueClient(DEFAULT_ACCOUNT_NAME, DEFAULT_ACCOUNT_KEY);
    }


    // AccountCredentials

    public static StorageCredentials newAccountKeyCredentials(String accountName, String accountKey) {
        return new StorageCredentialsAccountAndKey(accountName, accountKey);
    }

    public static StorageCredentials newAccountKeyCredentials() {
        return newAccountKeyCredentials(DEFAULT_ACCOUNT_NAME, DEFAULT_ACCOUNT_KEY);
    }
}
