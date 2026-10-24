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

import java.util.Map;

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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
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
@BodyTypes(consumerOut = @BodyType(value = { Carrier.STREAM, Carrier.FILE },
                                   description = "The content of the blob, one message per blob: a stream that reads it, or"
                                                 + " with fileDir the java.io.File it is downloaded to"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "uploadBlockBlob",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE }),
                                          @BodyType.When(option = "operation", value = "commitAppendBlob",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE }),
                                          @BodyType.When(option = "operation", value = "uploadPageBlob",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE }),
                                          @BodyType.When(option = "operation", value = "stageBlockBlobList",
                                                         carriers = { Carrier.LIST, Carrier.POJO }),
                                          @BodyType.When(option = "operation", value = "commitBlobBlockList",
                                                         carriers = { Carrier.LIST, Carrier.POJO }) },
                                  description = "The content to upload for uploadBlockBlob, commitAppendBlob and uploadPageBlob:"
                                                + " text, bytes, a stream or a file; a Map or a List fails: marshal it first. A"
                                                + " List of BlobBlock for stageBlockBlobList, and of Block for"
                                                + " commitBlobBlockList. getBlob writes into an OutputStream body. The other"
                                                + " operations do not use the body, or take their value from a header first: the"
                                                + " file of uploadBlockBlobChunked, the tags, the tag filter, the legal hold, the"
                                                + " tier and the policy"),
           producerOut = @BodyType(value = Carrier.LIST,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "getBlob", carriers = Carrier.STREAM),
                                           @BodyType.When(option = "operation", value = "downloadBlobToFile",
                                                          carriers = Carrier.FILE,
                                                          javaType = "java.io.File"),
                                           @BodyType.When(option = "operation", value = "getBlobTags", carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<String, String>"),
                                           @BodyType.When(option = "operation", value = "getPageBlobRanges",
                                                          carriers = Carrier.ITERATOR,
                                                          javaType = "com.azure.core.http.rest.PagedIterable<com.azure.storage.blob.models.PageRangeItem>"),
                                           @BodyType.When(option = "operation", value = "getBlobBlockList",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.azure.storage.blob.models.BlockList"),
                                           @BodyType.When(option = "operation", value = "setBlobImmutabilityPolicy",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.azure.storage.blob.models.BlobImmutabilityPolicy"),
                                           @BodyType.When(option = "operation", value = "setBlobTier", carriers = Carrier.POJO,
                                                          javaType = "com.azure.storage.blob.models.AccessTier"),
                                           @BodyType.When(option = "operation", value = "copyBlob", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "createBlobSnapshot",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "setBlobLegalHold",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "createBlobContainer",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "deleteBlobContainer",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "deleteBlob", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "downloadLink",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "uploadBlockBlob",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "uploadBlockBlobChunked",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "stageBlockBlobList",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "commitBlobBlockList",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "createAppendBlob",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "commitAppendBlob",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "createPageBlob",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "uploadPageBlob",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "resizePageBlob",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "clearPageBlob",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "setBlobTags", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "undeleteBlob",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean") },
                                   description = "A List of the items of the SDK for listBlobContainers, the default,"
                                                 + " findBlobsByTags, listBlobs, listBlobVersions and getChangeFeed. A stream of"
                                                 + " the content for getBlob, or the OutputStream body it wrote into; the"
                                                 + " java.io.File for downloadBlobToFile; true for the operations that create,"
                                                 + " upload, set or delete, with their results in headers, such as the link of"
                                                 + " downloadLink"))
public class BlobEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

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

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(configuration.getAccountName())
                && ObjectHelper.isNotEmpty(configuration.getContainerName())) {
            return "azure-storage-blob:" + configuration.getAccountName() + "/" + configuration.getContainerName();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "storage-blob";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getBlobName() != null) {
            return Map.of("blobName", configuration.getBlobName());
        }
        return null;
    }
}
