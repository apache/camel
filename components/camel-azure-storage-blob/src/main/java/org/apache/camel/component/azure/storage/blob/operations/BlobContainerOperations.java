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
import java.util.Map;

import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.models.PublicAccessType;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobExchangeHeaders;
import org.apache.camel.component.azure.storage.blob.client.BlobContainerClientWrapper;
import org.apache.camel.util.ObjectHelper;

/**
 * All operations related to {@link com.azure.storage.blob.BlobContainerClient}. This is at the container level.
 */
public class BlobContainerOperations {

    private final BlobContainerClientWrapper client;

    public BlobContainerOperations(final BlobContainerClientWrapper client) {
        ObjectHelper.notNull(client, "client cannot be null");

        this.client = client;
    }

    public BlobOperationResponse listBlobs(final Exchange exchange) {
        if (exchange == null) {
            return new BlobOperationResponse(client.listBlobs(new ListBlobsOptions(), null));
        }

        final ListBlobsOptions listBlobOptions = getListBlobOptions(exchange);
        final Duration timeout = BlobExchangeHeaders.getTimeoutFromHeaders(exchange);

        return new BlobOperationResponse(client.listBlobs(listBlobOptions, timeout));
    }

    public BlobOperationResponse createContainer(final Exchange exchange) {
        if (exchange == null) {
            final BlobExchangeHeaders blobExchangeHeaders = new BlobExchangeHeaders().httpHeaders(client.createContainer(null, null, null));
            return new BlobOperationResponse(true, blobExchangeHeaders.toMap());
        }

        final Map<String, String> metadata = BlobExchangeHeaders.getMetadataFromHeaders(exchange);
        final PublicAccessType publicAccessType = BlobExchangeHeaders.getPublicAccessTypeFromHeaders(exchange);
        final Duration timeout = BlobExchangeHeaders.getTimeoutFromHeaders(exchange);

        final BlobExchangeHeaders blobExchangeHeaders = new BlobExchangeHeaders().httpHeaders(client.createContainer(metadata, publicAccessType, timeout));

        return new BlobOperationResponse(true, blobExchangeHeaders.toMap());
    }

    public BlobOperationResponse deleteContainer(final Exchange exchange) {
        if (exchange == null) {
            final BlobExchangeHeaders blobExchangeHeaders = new BlobExchangeHeaders().httpHeaders(client.deleteContainer(null, null));
            return new BlobOperationResponse(true, blobExchangeHeaders.toMap());
        }

        final BlobRequestConditions blobRequestConditions = BlobExchangeHeaders.getBlobRequestConditionsFromHeaders(exchange);
        final Duration timeout = BlobExchangeHeaders.getTimeoutFromHeaders(exchange);

        final BlobExchangeHeaders blobExchangeHeaders = new BlobExchangeHeaders().httpHeaders(client.deleteContainer(blobRequestConditions, timeout));

        return new BlobOperationResponse(true, blobExchangeHeaders.toMap());
    }

    private ListBlobsOptions getListBlobOptions(final Exchange exchange) {
        ListBlobsOptions blobsOptions = BlobExchangeHeaders.getListBlobsOptionsFromHeaders(exchange);

        if (!ObjectHelper.isEmpty(blobsOptions)) {
            return blobsOptions;
        } else {
            blobsOptions = new ListBlobsOptions();
        }

        final BlobListDetails blobListDetails = BlobExchangeHeaders.getBlobListDetailsFromHeaders(exchange);
        final String prefix = BlobExchangeHeaders.getPrefixFromHeaders(exchange);
        final Integer maxResultsPerPage = BlobExchangeHeaders.getMaxResultsPerPageFromHeaders(exchange);

        blobsOptions.setDetails(blobListDetails);
        blobsOptions.setMaxResultsPerPage(maxResultsPerPage);
        blobsOptions.setPrefix(prefix);

        return blobsOptions;
    }
}
