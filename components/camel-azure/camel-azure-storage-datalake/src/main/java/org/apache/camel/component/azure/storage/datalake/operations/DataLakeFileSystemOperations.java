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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.azure.storage.file.datalake.models.DataLakeRequestConditions;
import com.azure.storage.file.datalake.models.ListPathsOptions;
import com.azure.storage.file.datalake.models.PathItem;
import com.azure.storage.file.datalake.models.PublicAccessType;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.datalake.DataLakeConfiguration;
import org.apache.camel.component.azure.storage.datalake.DataLakeConfigurationOptionsProxy;
import org.apache.camel.component.azure.storage.datalake.DataLakeExchangeHeaders;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeFileSystemClientWrapper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataLakeFileSystemOperations {
    private static final Logger LOG = LoggerFactory.getLogger(DataLakeFileSystemOperations.class);
    private final DataLakeFileSystemClientWrapper client;
    private final DataLakeConfigurationOptionsProxy configurationProxy;

    public DataLakeFileSystemOperations(final DataLakeConfiguration configuration,
                                        final DataLakeFileSystemClientWrapper client) {
        ObjectHelper.notNull(client, "client cannot be null");
        this.client = client;
        configurationProxy = new DataLakeConfigurationOptionsProxy(configuration);
    }

    public DataLakeOperationResponse createFileSystem(final Exchange exchange) {
        LOG.info("inside create file system in operation");
        final Map<String, String> metadata = configurationProxy.getMetadata(exchange);
        final PublicAccessType publicAccessType = configurationProxy.getPublicAccessType(exchange);
        final Duration timeout = configurationProxy.getTimeout(exchange);
        final DataLakeExchangeHeaders dataLakeExchangeHeaders
                = new DataLakeExchangeHeaders().httpHeaders(client.createFileSystem(metadata, publicAccessType, timeout));

        LOG.info("DataLake exchange headers: {}", dataLakeExchangeHeaders);

        return new DataLakeOperationResponse(true, dataLakeExchangeHeaders.toMap());
    }

    public DataLakeOperationResponse deleteFileSystem(final Exchange exchange) {
        final DataLakeRequestConditions dataLakeRequestConditions = configurationProxy.getDataLakeRequestConditions(exchange);
        final Duration timeout = configurationProxy.getTimeout(exchange);
        final DataLakeExchangeHeaders dataLakeExchangeHeaders
                = new DataLakeExchangeHeaders().httpHeaders(client.deleteFileSystem(dataLakeRequestConditions, timeout));
        return new DataLakeOperationResponse(true, dataLakeExchangeHeaders.toMap());
    }

    public DataLakeOperationResponse listPaths(final Exchange exchange) {
        final ListPathsOptions listPathsOptions = configurationProxy.getListPathOptions(exchange);
        final Duration timeout = configurationProxy.getTimeout(exchange);
        final String regex = configurationProxy.getRegex(exchange);
        List<PathItem> paths = client.listPaths(listPathsOptions, timeout);

        if (ObjectHelper.isEmpty(regex)) {
            return new DataLakeOperationResponse(paths);
        } else {
            List<PathItem> filteredPaths = paths.stream()
                    .filter(x -> x.getName().matches(regex))
                    .collect(Collectors.toCollection(LinkedList<PathItem>::new));
            return new DataLakeOperationResponse(filteredPaths);
        }
    }

}
