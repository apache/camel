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
package org.apache.camel.component.azure.queue;

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
 * The azure-queue component is used for storing and retrieving messages from Azure Storage Queue Service.
 */
@UriEndpoint(firstVersion = "2.19.0",
             scheme = "azure-queue",
             title = "Azure Storage Queue Service", 
             syntax = "azure-blob:containerAndQueueUri", 
             label = "cloud,queue,azure")
public class QueueServiceEndpoint extends DefaultEndpoint {

    @UriPath(description = "Container Queue compact Uri")
    @Metadata(required = true)
    private String containerAndQueueUri; // to support component docs
    @UriParam
    private QueueServiceConfiguration configuration;

    public QueueServiceEndpoint(String uri, Component comp, QueueServiceConfiguration configuration) {
        super(uri, comp);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        QueueServiceConsumer consumer = new QueueServiceConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new QueueServiceProducer(this);
    }

    public QueueServiceConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(QueueServiceConfiguration configuration) {
        this.configuration = configuration;
    }

}
