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
package org.apache.camel.component.azure.blob;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * The azure-blob component is used for storing and retrieving blobs from Azure Storage Blob Service.
 */
@UriEndpoint(firstVersion = "2.19.0",
             scheme = "azure-blob",
             title = "Azure Storage Blob Service", 
             syntax = "azure-blob:containerOrBlobUri", 
             label = "cloud,database,nosql")
public class BlobServiceEndpoint extends DefaultEndpoint {

    @UriPath(description = "Container or Blob compact Uri")
    @Metadata(required = true)
    private String containerOrBlobUri; // to support component docs
    @UriParam
    private BlobServiceConfiguration configuration;

    public BlobServiceEndpoint(String uri, Component comp, BlobServiceConfiguration configuration) {
        super(uri, comp);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (getConfiguration().getBlobName() == null) {
            throw new IllegalArgumentException("Blob name must be specified.");
        }
        BlobServiceConsumer consumer = new BlobServiceConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        if (getConfiguration().getBlobName() == null
            && getConfiguration().getOperation() != null 
            && BlobServiceOperations.listBlobs != configuration.getOperation()) {
            // Omitting a blob name is only possible it is a (default) listBlobs producer operation
            throw new IllegalArgumentException("Blob name must be specified.");
        }
        return new BlobServiceProducer(this);
    }

    public BlobServiceConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(BlobServiceConfiguration configuration) {
        this.configuration = configuration;
    }

}
