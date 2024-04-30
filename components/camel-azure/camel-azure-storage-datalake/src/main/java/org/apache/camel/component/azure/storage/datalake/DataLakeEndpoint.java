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
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;

/**
 * Sends and receives files to/from Azure Data Lake Storage.
 */
@UriEndpoint(firstVersion = "3.8.0", scheme = "azure-storage-datalake", title = "Azure Storage Data Lake Service",
             syntax = "azure-storage-datalake:accountName/fileSystemName",
             category = { Category.CLOUD, Category.FILE, Category.BIGDATA }, headersClass = DataLakeConstants.class)
public class DataLakeEndpoint extends ScheduledPollEndpoint {

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

}
