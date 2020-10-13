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
package org.apache.camel.component.azure.storage.queue;

import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.models.QueueMessageItem;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.azure.storage.queue.client.QueueClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * The azure-storage-queue component is used for storing and retrieving the messages to/from Azure Storage Queue using
 * Azure SDK v12.
 */
@UriEndpoint(firstVersion = "3.3.0", scheme = "azure-storage-queue", title = "Azure Storage Queue Service",
             syntax = "azure-storage-queue:accountName/queueName", category = { Category.CLOUD, Category.MESSAGING })
public class QueueEndpoint extends DefaultEndpoint {

    private QueueServiceClient queueServiceClient;

    @UriParam
    private QueueConfiguration configuration;

    public QueueEndpoint(final String uri, final Component component, final QueueConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new QueueProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (ObjectHelper.isEmpty(configuration.getQueueName())) {
            throw new IllegalArgumentException("QueueName must be set.");
        }
        return new QueueConsumer(this, processor);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        queueServiceClient = configuration.getServiceClient() != null
                ? configuration.getServiceClient() : QueueClientFactory.createQueueServiceClient(configuration);
    }

    public Exchange createExchange(final QueueMessageItem messageItem) {
        final Exchange exchange = createExchange();
        final Message message = exchange.getIn();

        message.setBody(messageItem.getMessageText());
        message.setHeaders(QueueExchangeHeaders.createQueueExchangeHeadersFromQueueMessageItem(messageItem).toMap());

        return exchange;
    }

    /**
     * The component configurations
     */
    public QueueConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(QueueConfiguration configuration) {
        this.configuration = configuration;
    }

    public QueueServiceClient getQueueServiceClient() {
        return queueServiceClient;
    }
}
