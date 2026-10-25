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

import java.util.Map;

import com.azure.storage.queue.QueueServiceClient;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.azure.storage.queue.client.QueueClientFactory;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Stores and retrieves messages to/from Azure Storage Queue.
 */
@UriEndpoint(firstVersion = "3.3.0", scheme = "azure-storage-queue", title = "Azure Storage Queue Service",
             syntax = "azure-storage-queue:accountName/queueName", category = { Category.CLOUD, Category.MESSAGING },
             headersClass = QueueConstants.class)
@BodyTypes(consumerOut = @BodyType(value = { Carrier.STREAM, Carrier.NULL },
                                   description = "The text of the queue message as a stream, with its id and pop receipt"
                                                 + " in headers; no body for a message without text"),
           producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "updateMessage",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE,
                                                                 Carrier.NULL }),
                                          @BodyType.When(option = "operation", value = "listQueues", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "createQueue", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "deleteQueue", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "clearQueue", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "deleteMessage", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "receiveMessages",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "peekMessages",
                                                         carriers = Carrier.ANY) },
                                  description = "The text of the message for sendMessage, the default, and updateMessage: bytes,"
                                                + " a stream or a file are read as text, and a Map or a POJO is sent as its"
                                                + " toString(): marshal it first. sendMessage fails without a body, and"
                                                + " updateMessage then changes only the visibility. The other operations do not"
                                                + " use the body"),
           producerOut = @BodyType(value = Carrier.VALUE, javaType = "java.lang.Boolean",
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "listQueues", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.azure.storage.queue.models.QueueItem>"),
                                           @BodyType.When(option = "operation", value = "receiveMessages",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.azure.storage.queue.models.QueueMessageItem>"),
                                           @BodyType.When(option = "operation", value = "peekMessages", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.azure.storage.queue.models.PeekedMessageItem>") },
                                   description = "true after sendMessage, the default, and the operations that create, clear,"
                                                 + " update or delete, with their results in headers, such as the id and the pop"
                                                 + " receipt of the message sent. A List of the items of the SDK for listQueues,"
                                                 + " receiveMessages and peekMessages"))
public class QueueEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

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
        QueueConsumer consumer = new QueueConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        queueServiceClient = configuration.getServiceClient() != null
                ? configuration.getServiceClient() : QueueClientFactory.createQueueServiceClient(configuration);
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

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(configuration.getAccountName()) && ObjectHelper.isNotEmpty(configuration.getQueueName())) {
            return "azure-storage-queue:" + configuration.getAccountName() + "/" + configuration.getQueueName();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "storage-queue";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getCredentialType() != null) {
            return Map.of("credentialType", configuration.getCredentialType().name());
        }
        return null;
    }
}
