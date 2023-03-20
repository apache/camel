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
package org.apache.camel.component.azure.storage.datalake.operations;

import java.time.Duration;
import java.util.Map;

import com.azure.core.http.rest.Response;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.models.DataLakeRequestConditions;
import com.azure.storage.file.datalake.models.PathHttpHeaders;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.datalake.DataLakeConfiguration;
import org.apache.camel.component.azure.storage.datalake.DataLakeConfigurationOptionsProxy;
import org.apache.camel.component.azure.storage.datalake.DataLakeExchangeHeaders;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeDirectoryClientWrapper;
import org.apache.camel.util.ObjectHelper;

public class DataLakeDirectoryOperations {
    private final DataLakeDirectoryClientWrapper client;
    private final DataLakeConfigurationOptionsProxy configurationProxy;

    public DataLakeDirectoryOperations(final DataLakeConfiguration configuration, final DataLakeDirectoryClientWrapper client) {
        ObjectHelper.notNull(client, "client cannot be null");
        this.client = client;
        configurationProxy = new DataLakeConfigurationOptionsProxy(configuration);
    }

    public DataLakeOperationResponse createFile(final Exchange exchange) {
        final String fileName = configurationProxy.getFileName(exchange);
        final String permission = configurationProxy.getPermission(exchange);
        final String umask = configurationProxy.getUmask(exchange);
        final PathHttpHeaders httpHeaders = configurationProxy.getPathHttpHeaders(exchange);
        final Map<String, String> metadata = configurationProxy.getMetadata(exchange);
        final DataLakeRequestConditions requestConditions = configurationProxy.getDataLakeRequestConditions(exchange);
        final Duration timeout = configurationProxy.getTimeout(exchange);

        final Response<DataLakeFileClient> response = client.createFileWithResponse(fileName, permission, umask,
                httpHeaders, metadata, requestConditions, timeout);
        final DataLakeFileClient fileClient = response.getValue();
        final DataLakeExchangeHeaders exchangeHeaders
                = DataLakeExchangeHeaders.create().httpHeaders(response.getHeaders()).fileClient(fileClient);
        return new DataLakeOperationResponse(fileClient, exchangeHeaders.toMap());
    }

    public DataLakeOperationResponse deleteDirectory(final Exchange exchange) {
        final Boolean recursive = configurationProxy.isRecursive(exchange);
        final DataLakeRequestConditions requestConditions = configurationProxy.getDataLakeRequestConditions(exchange);
        final Duration timeout = configurationProxy.getTimeout(exchange);

        final Response<Void> response = client.deleteWithResponse(recursive, requestConditions, timeout);

        DataLakeExchangeHeaders exchangeHeaders = DataLakeExchangeHeaders.create().httpHeaders(response.getHeaders());

        return new DataLakeOperationResponse(true, exchangeHeaders.toMap());
    }
}
