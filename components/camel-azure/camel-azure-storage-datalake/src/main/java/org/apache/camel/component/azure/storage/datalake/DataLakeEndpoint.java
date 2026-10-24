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

import java.util.Map;

import com.azure.storage.file.datalake.DataLakeServiceClient;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeClientFactory;
import org.apache.camel.component.azure.storage.datalake.operations.DataLakeOperationResponse;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Sends and receives files to/from Azure Data Lake Storage.
 */
@UriEndpoint(firstVersion = "3.8.0", scheme = "azure-storage-datalake", title = "Azure Storage Data Lake Service",
             syntax = "azure-storage-datalake:accountName/fileSystemName",
             category = { Category.CLOUD, Category.FILE, Category.BIGDATA }, headersClass = DataLakeConstants.class)
@BodyTypes(consumerOut = @BodyType(value = { Carrier.STREAM, Carrier.FILE },
                                   description = "The content of the file, one message per file: a stream that reads it, or with"
                                                 + " fileDir the java.io.File it is downloaded to. The name of the file is in the"
                                                 + " CamelAzureStorageDataLakeFileName header; without fileName, every file of"
                                                 + " the file system is consumed, the directories skipped"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "upload",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE }),
                                          @BodyType.When(option = "operation", value = "appendToFile",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE }) },
                                  description = "The content to write for upload and appendToFile: text, bytes, a stream or a"
                                                + " file; a Map or no body fails: marshal it first. getFile writes into an"
                                                + " OutputStream body. The other operations do not use the body: uploadFromFile"
                                                + " reads the file of the path option"),
           producerOut = @BodyType(value = Carrier.LIST,
                                   javaType = "java.util.List<com.azure.storage.file.datalake.models.FileSystemItem>",
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "createFileSystem",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "deleteFileSystem",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "deleteFile", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "appendToFile",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "uploadFromFile",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "upload", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "deleteDirectory",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "listPaths", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.azure.storage.file.datalake.models.PathItem>"),
                                           @BodyType.When(option = "operation", value = "getFile", carriers = Carrier.STREAM),
                                           @BodyType.When(option = "operation", value = "openQueryInputStream",
                                                          carriers = Carrier.STREAM),
                                           @BodyType.When(option = "operation", value = "downloadToFile",
                                                          carriers = Carrier.FILE,
                                                          javaType = "java.io.File"),
                                           @BodyType.When(option = "operation", value = "downloadLink", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "flushToFile", carriers = Carrier.POJO,
                                                          javaType = "com.azure.storage.file.datalake.models.PathInfo"),
                                           @BodyType.When(option = "operation", value = "createFile", carriers = Carrier.POJO,
                                                          javaType = "com.azure.storage.file.datalake.DataLakeFileClient") },
                                   description = "A List of the items of the SDK for listFileSystem, the default, and listPaths."
                                                 + " A stream of the content for getFile, or the OutputStream body it wrote"
                                                 + " into, and of the query result for openQueryInputStream; the java.io.File for"
                                                 + " downloadToFile; the link for downloadLink; the PathInfo for flushToFile and"
                                                 + " the DataLakeFileClient for createFile; true for createFileSystem and the"
                                                 + " operations that upload, append or delete, with their results in headers"))
public class DataLakeEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    @UriParam(description = "service client of data lake")
    private DataLakeServiceClient dataLakeServiceClient;

    @UriParam(description = "configuration object of azure data lake")
    private DataLakeConfiguration configuration;

    public DataLakeEndpoint() {
    }

    public DataLakeEndpoint(final String uri, final Component component, final DataLakeConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new DataLakeProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        DataLakeConsumer consumer = new DataLakeConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public DataLakeConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(DataLakeConfiguration configuration) {
        this.configuration = configuration;
    }

    public DataLakeServiceClient getDataLakeServiceClient() {
        return dataLakeServiceClient;
    }

    public void setDataLakeServiceClient(DataLakeServiceClient dataLakeServiceClient) {
        this.dataLakeServiceClient = dataLakeServiceClient;
    }

    public void setResponseOnExchange(final DataLakeOperationResponse response, final Exchange exchange) {
        final Message message = exchange.getIn();
        message.setBody(response.getBody());
        message.setHeaders(response.getHeaders());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (configuration.getServiceClient() != null) {
            dataLakeServiceClient = configuration.getServiceClient();
        } else {
            dataLakeServiceClient = DataLakeClientFactory.createDataLakeServiceClient(configuration);
        }
    }

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(configuration.getFileSystemName())) {
            return configuration.getFileSystemName();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "datalake";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (ObjectHelper.isNotEmpty(configuration.getAccountName())) {
            return Map.of("account", configuration.getAccountName());
        }
        return null;
    }
}
