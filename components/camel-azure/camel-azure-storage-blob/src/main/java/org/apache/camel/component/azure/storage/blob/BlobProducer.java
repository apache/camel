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

import com.azure.storage.blob.changefeed.BlobChangefeedClient;
import com.azure.storage.blob.changefeed.BlobChangefeedClientBuilder;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.client.BlobClientWrapper;
import org.apache.camel.component.azure.storage.blob.client.BlobServiceClientWrapper;
import org.apache.camel.component.azure.storage.blob.operations.BlobChangeFeedOperations;
import org.apache.camel.component.azure.storage.blob.operations.BlobContainerOperations;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperationResponse;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperations;
import org.apache.camel.component.azure.storage.blob.operations.BlobServiceOperations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * A Producer which sends messages to the Azure Storage Blob Service
 */
public class BlobProducer extends DefaultProducer {

    private final BlobConfiguration configuration;
    private final BlobConfigurationOptionsProxy configurationProxy;
    private final BlobServiceOperations blobServiceOperations;
    private final BlobServiceClientWrapper blobServiceClientWrapper;

    public BlobProducer(final Endpoint endpoint) {
        super(endpoint);
        this.configuration = getEndpoint().getConfiguration();
        this.blobServiceClientWrapper = new BlobServiceClientWrapper(getEndpoint().getBlobServiceClient());
        this.blobServiceOperations = new BlobServiceOperations(configuration, blobServiceClientWrapper);
        this.configurationProxy = new BlobConfigurationOptionsProxy(configuration);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        BlobOperationsDefinition operation = determineOperation(exchange);

        if (ObjectHelper.isEmpty(operation)) {
            operation = BlobOperationsDefinition.listBlobContainers;
        }

        switch (operation) {
            // service operations
            case listBlobContainers:
                setResponse(exchange, blobServiceOperations.listBlobContainers(exchange));
                break;
            // container operations
            case createBlobContainer:
                setResponse(exchange, getContainerOperations(exchange).createContainer(exchange));
                break;
            case deleteBlobContainer:
                setResponse(exchange, getContainerOperations(exchange).deleteContainer(exchange));
                break;
            case listBlobs:
                setResponse(exchange, getContainerOperations(exchange).listBlobs(exchange));
                break;
            // blob operations
            case getBlob:
                setResponse(exchange, getBlobOperations(exchange).getBlob(exchange));
                break;
            case deleteBlob:
                setResponse(exchange, getBlobOperations(exchange).deleteBlob(exchange));
                break;
            case downloadBlobToFile:
                setResponse(exchange, getBlobOperations(exchange).downloadBlobToFile(exchange));
                break;
            case downloadLink:
                setResponse(exchange, getBlobOperations(exchange).downloadLink(exchange));
                break;
            // block blob operations
            case uploadBlockBlob:
                setResponse(exchange, getBlobOperations(exchange).uploadBlockBlob(exchange));
                break;
            case stageBlockBlobList:
                setResponse(exchange, getBlobOperations(exchange).stageBlockBlobList(exchange));
                break;
            case commitBlobBlockList:
                setResponse(exchange, getBlobOperations(exchange).commitBlobBlockList(exchange));
                break;
            case getBlobBlockList:
                setResponse(exchange, getBlobOperations(exchange).getBlobBlockList(exchange));
                break;
            // append blob operations
            case createAppendBlob:
                setResponse(exchange, getBlobOperations(exchange).createAppendBlob(exchange));
                break;
            case commitAppendBlob:
                setResponse(exchange, getBlobOperations(exchange).commitAppendBlob(exchange));
                break;
            // page blob operations
            case createPageBlob:
                setResponse(exchange, getBlobOperations(exchange).createPageBlob(exchange));
                break;
            case uploadPageBlob:
                setResponse(exchange, getBlobOperations(exchange).uploadPageBlob(exchange));
                break;
            case resizePageBlob:
                setResponse(exchange, getBlobOperations(exchange).resizePageBlob(exchange));
                break;
            case clearPageBlob:
                setResponse(exchange, getBlobOperations(exchange).clearPageBlob(exchange));
                break;
            case getPageBlobRanges:
                setResponse(exchange, getBlobOperations(exchange).getPageBlobRanges(exchange));
                break;
            case getChangeFeed:
                setResponse(exchange, getBlobChangeFeedOperations().getEvents(exchange));
                break;
            case copyBlob:
                setResponse(exchange, getBlobOperations(exchange).copyBlob(exchange));
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private void setResponse(final Exchange exchange, final BlobOperationResponse blobOperationResponse) {
        exchange.getMessage().setBody(blobOperationResponse.getBody());
        exchange.getMessage().getHeaders().putAll(blobOperationResponse.getHeaders());
    }

    @Override
    public BlobEndpoint getEndpoint() {
        return (BlobEndpoint) super.getEndpoint();
    }

    private BlobOperationsDefinition determineOperation(final Exchange exchange) {
        return configurationProxy.getOperation(exchange);
    }

    private BlobContainerOperations getContainerOperations(final Exchange exchange) {
        return new BlobContainerOperations(
                configuration, blobServiceClientWrapper.getBlobContainerClientWrapper(determineContainerName(exchange)));
    }

    private BlobOperations getBlobOperations(final Exchange exchange) {
        final BlobClientWrapper clientWrapper
                = blobServiceClientWrapper.getBlobContainerClientWrapper(determineContainerName(exchange))
                        .getBlobClientWrapper(determineBlobName(exchange));

        return new BlobOperations(configuration, clientWrapper);
    }

    private BlobChangeFeedOperations getBlobChangeFeedOperations() {
        final BlobChangefeedClient changefeedClient
                = new BlobChangefeedClientBuilder(getEndpoint().getBlobServiceClient()).buildClient();

        return new BlobChangeFeedOperations(changefeedClient, configurationProxy);
    }

    private String determineContainerName(final Exchange exchange) {
        final String containerName = configurationProxy.getContainerName(exchange);

        if (ObjectHelper.isEmpty(containerName)) {
            throw new IllegalArgumentException("Container name must be specified");
        }
        return containerName;
    }

    public String determineBlobName(final Exchange exchange) {
        final String blobName = configurationProxy.getBlobName(exchange);

        if (ObjectHelper.isEmpty(blobName)) {
            throw new IllegalArgumentException("Blob name must be specified");
        }
        return blobName;
    }
}
