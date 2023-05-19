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
package org.apache.camel.component.azure.storage.blob.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.azure.core.http.HttpHeaders;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.models.PublicAccessType;
import org.apache.camel.util.ObjectHelper;

public class BlobContainerClientWrapper {

    private final BlobContainerClient client;

    public BlobContainerClientWrapper(final BlobContainerClient client) {
        this.client = client;
    }

    public HttpHeaders createContainer(
            final Map<String, String> metadata, final PublicAccessType publicAccessType, final Duration timeout) {
        return client.createWithResponse(metadata, publicAccessType, timeout, Context.NONE).getHeaders();
    }

    public HttpHeaders deleteContainer(final BlobRequestConditions blobRequestConditions, final Duration timeout) {
        return client.deleteWithResponse(blobRequestConditions, timeout, Context.NONE).getHeaders();
    }

    public List<BlobItem> listBlobs(final ListBlobsOptions listBlobsOptions, final Duration timeout) {
        return client.listBlobs(listBlobsOptions, timeout).stream().toList();
    }

    public BlobClientWrapper getBlobClientWrapper(final String blobName) {
        if (!ObjectHelper.isEmpty(blobName)) {
            return new BlobClientWrapper(client.getBlobClient(blobName));
        }
        throw new IllegalArgumentException("Cannot initialize a blob since no blob name was provided.");
    }
}
