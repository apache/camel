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
package org.apache.camel.component.azure.storage.blob.operations;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.models.PublicAccessType;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobConfigurationOptionsProxy;
import org.apache.camel.component.azure.storage.blob.BlobExchangeHeaders;
import org.apache.camel.component.azure.storage.blob.client.BlobContainerClientWrapper;
import org.apache.camel.util.ObjectHelper;

/**
 * All operations related to {@link com.azure.storage.blob.BlobContainerClient}. This is at the container level.
 */
public class BlobContainerOperations {

    private final BlobContainerClientWrapper client;
    private final BlobConfigurationOptionsProxy configurationProxy;

    public BlobContainerOperations(final BlobConfiguration configuration, final BlobContainerClientWrapper client) {
        ObjectHelper.notNull(client, "client cannot be null");
        this.client = client;
        this.configurationProxy = new BlobConfigurationOptionsProxy(configuration);
    }

    public BlobOperationResponse listBlobs(final Exchange exchange) {
        final ListBlobsOptions listBlobOptions = configurationProxy.getListBlobOptions(exchange);
        final Duration timeout = configurationProxy.getTimeout(exchange);
        final String regex = configurationProxy.getRegex(exchange);
        List<BlobItem> blobs = client.listBlobs(listBlobOptions, timeout);
        if (ObjectHelper.isEmpty(regex)) {
            return BlobOperationResponse.create(blobs);
        }
        List<BlobItem> filteredBlobs = blobs.stream()
                .filter(x -> x.getName().matches(regex))
                .collect(Collectors.toCollection(LinkedList<BlobItem>::new));
        return BlobOperationResponse.create(filteredBlobs);
    }

    public BlobOperationResponse createContainer(final Exchange exchange) {
        final Map<String, String> metadata = configurationProxy.getMetadata(exchange);
        final PublicAccessType publicAccessType = configurationProxy.getPublicAccessType(exchange);
        final Duration timeout = configurationProxy.getTimeout(exchange);

        final BlobExchangeHeaders blobExchangeHeaders
                = new BlobExchangeHeaders().httpHeaders(client.createContainer(metadata, publicAccessType, timeout));
        return BlobOperationResponse.createWithEmptyBody(blobExchangeHeaders.toMap());
    }

    public BlobOperationResponse deleteContainer(final Exchange exchange) {
        final BlobRequestConditions blobRequestConditions = configurationProxy.getBlobRequestConditions(exchange);
        final Duration timeout = configurationProxy.getTimeout(exchange);
        final BlobExchangeHeaders blobExchangeHeaders
                = new BlobExchangeHeaders().httpHeaders(client.deleteContainer(blobRequestConditions, timeout));
        return BlobOperationResponse.createWithEmptyBody(blobExchangeHeaders.toMap());
    }
}
