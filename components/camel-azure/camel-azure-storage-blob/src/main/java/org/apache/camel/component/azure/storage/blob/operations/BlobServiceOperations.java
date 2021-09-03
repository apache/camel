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

import com.azure.storage.blob.models.ListBlobContainersOptions;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobConfigurationOptionsProxy;
import org.apache.camel.component.azure.storage.blob.client.BlobServiceClientWrapper;
import org.apache.camel.util.ObjectHelper;

/**
 * Operations related to {@link com.azure.storage.blob.BlobServiceClient}. This is at the service level.
 */
public class BlobServiceOperations {

    private final BlobServiceClientWrapper client;
    private final BlobConfigurationOptionsProxy configurationProxy;

    public BlobServiceOperations(final BlobConfiguration configuration, final BlobServiceClientWrapper client) {
        ObjectHelper.notNull(client, "client cannot be null");

        this.client = client;
        this.configurationProxy = new BlobConfigurationOptionsProxy(configuration);
    }

    public BlobOperationResponse listBlobContainers(final Exchange exchange) {
        final ListBlobContainersOptions listBlobContainersOptions = configurationProxy.getListBlobContainersOptions(exchange);
        final Duration timeout = configurationProxy.getTimeout(exchange);

        return BlobOperationResponse.create(client.listBlobContainers(listBlobContainersOptions, timeout));
    }
}
