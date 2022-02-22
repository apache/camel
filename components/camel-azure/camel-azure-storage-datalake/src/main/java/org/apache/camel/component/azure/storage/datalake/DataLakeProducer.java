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
package org.apache.camel.component.azure.storage.datalake;

import java.io.IOException;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeDirectoryClientWrapper;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeFileClientWrapper;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeFileSystemClientWrapper;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeServiceClientWrapper;
import org.apache.camel.component.azure.storage.datalake.operations.DataLakeDirectoryOperations;
import org.apache.camel.component.azure.storage.datalake.operations.DataLakeFileOperations;
import org.apache.camel.component.azure.storage.datalake.operations.DataLakeFileSystemOperations;
import org.apache.camel.component.azure.storage.datalake.operations.DataLakeOperationResponse;
import org.apache.camel.component.azure.storage.datalake.operations.DataLakeServiceOperations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class DataLakeProducer extends DefaultProducer {
    private final DataLakeConfiguration configuration;
    private final DataLakeConfigurationOptionsProxy configurationProxy;
    private final DataLakeServiceClientWrapper dataLakeServiceClientWrapper;

    public DataLakeProducer(final Endpoint endpoint) {
        super(endpoint);
        configuration = getEndpoint().getConfiguration();
        configurationProxy = new DataLakeConfigurationOptionsProxy(configuration);
        dataLakeServiceClientWrapper = new DataLakeServiceClientWrapper(getEndpoint().getDataLakeServiceClient());
    }

    @Override
    public DataLakeEndpoint getEndpoint() {
        return (DataLakeEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws IllegalArgumentException, IOException {
        DataLakeOperationsDefinition operation = determineOperation(exchange);
        switch (operation) {
            case listFileSystem:
                setResponse(exchange, getServiceOperations().listFileSystems(exchange));
                break;
            case createFileSystem:
                setResponse(exchange, getFileSystemOperations(exchange).createFileSystem(exchange));
                break;
            case deleteFileSystem:
                setResponse(exchange, getFileSystemOperations(exchange).deleteFileSystem(exchange));
                break;
            case listPaths:
                setResponse(exchange, getFileSystemOperations(exchange).listPaths(exchange));
                break;
            case getFile:
                setResponse(exchange, getFileOperations(exchange).getFile(exchange));
                break;
            case downloadToFile:
                setResponse(exchange, getFileOperations(exchange).downloadToFile(exchange));
                break;
            case downloadLink:
                setResponse(exchange, getFileOperations(exchange).downloadLink(exchange));
                break;
            case deleteFile:
                setResponse(exchange, getFileOperations(exchange).deleteFile(exchange));
                break;
            case appendToFile:
                setResponse(exchange, getFileOperations(exchange).appendToFile(exchange));
                break;
            case flushToFile:
                setResponse(exchange, getFileOperations(exchange).flushToFile(exchange));
                break;
            case uploadFromFile:
                setResponse(exchange, getFileOperations(exchange).uploadFromFile(exchange));
                break;
            case openQueryInputStream:
                setResponse(exchange, getFileOperations(exchange).openQueryInputStream(exchange));
                break;
            case upload:
                setResponse(exchange, getFileOperations(exchange).upload(exchange));
                break;
            case createFile:
                setResponse(exchange, getDirectoryOperations(exchange).createFile(exchange));
                break;
            case deleteDirectory:
                setResponse(exchange, getDirectoryOperations(exchange).deleteDirectory(exchange));
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private void setResponse(final Exchange exchange, final DataLakeOperationResponse dataLakeOperationResponse) {
        exchange.getMessage().setBody(dataLakeOperationResponse.getBody());
        exchange.getMessage().getHeaders().putAll(dataLakeOperationResponse.getHeaders());
    }

    private DataLakeOperationsDefinition determineOperation(final Exchange exchange) {
        return configurationProxy.getOperation(exchange);
    }

    private DataLakeServiceOperations getServiceOperations() {
        return new DataLakeServiceOperations(configuration, dataLakeServiceClientWrapper);
    }

    private DataLakeFileSystemOperations getFileSystemOperations(final Exchange exchange) {
        final DataLakeFileSystemClientWrapper clientWrapper
                = dataLakeServiceClientWrapper.getDataLakeFileSystemClientWrapper(determineFileSystemName(exchange));

        return new DataLakeFileSystemOperations(configuration, clientWrapper);
    }

    private DataLakeDirectoryOperations getDirectoryOperations(final Exchange exchange) {
        final DataLakeDirectoryClientWrapper clientWrapper
                = dataLakeServiceClientWrapper.getDataLakeFileSystemClientWrapper(determineFileSystemName(exchange))
                        .getDataLakeDirectoryClientWrapper(determineDirectoryName(exchange));

        return new DataLakeDirectoryOperations(configuration, clientWrapper);
    }

    private DataLakeFileOperations getFileOperations(final Exchange exchange) {
        final DataLakeFileClientWrapper clientWrapper
                = dataLakeServiceClientWrapper.getDataLakeFileSystemClientWrapper(determineFileSystemName(exchange))
                        .getDataLakeFileClientWrapper(determineFileName(exchange));

        return new DataLakeFileOperations(configuration, clientWrapper);
    }

    private String determineFileSystemName(final Exchange exchange) {
        final String fileSystemName = configurationProxy.getFileSystemName(exchange);
        if (ObjectHelper.isEmpty(fileSystemName)) {
            throw new IllegalArgumentException("File system name must be specified.");
        }
        return fileSystemName;
    }

    private String determineDirectoryName(final Exchange exchange) {
        final String directoryName = configurationProxy.getDirectoryName(exchange);

        if (ObjectHelper.isEmpty(directoryName)) {
            throw new IllegalArgumentException("Directory name must be specified");
        }
        return directoryName;
    }

    private String determineFileName(final Exchange exchange) {
        final String fileName = configurationProxy.getFileName(exchange);
        if (ObjectHelper.isEmpty(fileName)) {
            throw new IllegalArgumentException("File name must be specified");
        }
        return fileName;
    }

}
