/**
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
package org.apache.camel.component.azure.storage;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.file.CloudFileClient;
import com.microsoft.azure.storage.queue.CloudQueueClient;
import com.microsoft.azure.storage.table.CloudTableClient;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class StorageConfiguration {
    @UriPath(description = "Azure Storage resource name") @Metadata(required = "true")
    private String resource;
    @UriParam(description = "Azure Storage account name")
    private String account;
    @UriParam(description = "Azure Storage primary or secondary key")
    private String key;
    @UriParam(description = "Protocol used for API calls to Azure Storage", enums = "https,http")
    private Protocol protocol = Protocol.https;

    private enum Protocol {
        https, http
    }

    private CloudStorageAccount storageAccount;
    private CloudQueueClient queueClient;
    private CloudBlobClient blobClient;
    private CloudTableClient tableClient;
    private CloudFileClient fileClient;

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getProtocol() {
        return protocol.toString();
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public CloudStorageAccount getStorageAccount() {
        return storageAccount;
    }

    public void setStorageAccount(CloudStorageAccount storageAccount) {
        this.storageAccount = storageAccount;
    }

    public CloudQueueClient getQueueClient() {
        return queueClient;
    }

    public void setQueueClient(CloudQueueClient queueClient) {
        this.queueClient = queueClient;
    }

    public CloudBlobClient getBlobClient() {
        return blobClient;
    }

    public void setBlobClient(CloudBlobClient blobClient) {
        this.blobClient = blobClient;
    }

    public CloudTableClient getTableClient() {
        return tableClient;
    }

    public void setTableClient(CloudTableClient tableClient) {
        this.tableClient = tableClient;
    }

    public CloudFileClient getFileClient() {
        return fileClient;
    }

    public void setFileClient(CloudFileClient fileClient) {
        this.fileClient = fileClient;
    }

    public String getConnectionString() {
        return String.format("DefaultEndpointsProtocol=%s;AccountName=%s;AccountKey=%s", getProtocol(), getAccount(), getKey());
    }
}
