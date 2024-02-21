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

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.azure.storage.blob.client.BlobClientFactory;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperationResponse;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Store and retrieve blobs from Azure Storage Blob Service.
 */
@UriEndpoint(firstVersion = "3.3.0", scheme = "azure-storage-blob", title = "Azure Storage Blob Service",
             syntax = "azure-storage-blob:accountName/containerName", category = { Category.CLOUD, Category.FILE },
             headersClass = BlobConstants.class)
public class BlobEndpoint extends ScheduledPollEndpoint {

    @UriParam
    private BlobServiceClient blobServiceClient;

    @UriParam
    private BlobConfiguration configuration;

    public BlobEndpoint(final String uri, final Component component, final BlobConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() {
        return new BlobProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        // we need blobname as well as blob container in order to create it
        if (ObjectHelper.isEmpty(configuration.getContainerName())) {
            throw new IllegalArgumentException("Container name must be set.");
        }
        BlobConsumer consumer = new BlobConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        blobServiceClient = configuration.getServiceClient() != null
                ? configuration.getServiceClient() : BlobClientFactory.createBlobServiceClient(configuration);
    }

    public void setResponseOnExchange(final BlobOperationResponse response, final Exchange exchange) {
        final Message message = exchange.getIn();

        message.setBody(response.getBody());
        message.setHeaders(response.getHeaders());
    }

    /**
     * The component configurations
     */
    public BlobConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(BlobConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Client to a storage account. This client does not hold any state about a particular storage account but is
     * instead a convenient way of sending off appropriate requests to the resource on the service. It may also be used
     * to construct URLs to blobs and containers.
     *
     * This client contains operations on a service account. Operations on a container are available on
     * {@link BlobContainerClient} through {@link #getBlobContainerClient(String)}, and operations on a blob are
     * available on {@link BlobClient} through {@link #getBlobContainerClient(String).getBlobClient(String)}.
     */
    public BlobServiceClient getBlobServiceClient() {
        return blobServiceClient;
    }

    public void setBlobServiceClient(BlobServiceClient blobServiceClient) {
        this.blobServiceClient = blobServiceClient;
    }
}
