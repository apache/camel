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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.queue.client.QueueServiceClientWrapper;
import org.apache.camel.component.azure.storage.queue.operations.QueueOperationResponse;
import org.apache.camel.component.azure.storage.queue.operations.QueueOperations;
import org.apache.camel.component.azure.storage.queue.operations.QueueServiceOperations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * A Producer which sends messages to the Azure Storage Queue Service
 */
public class QueueProducer extends DefaultProducer {

    private final QueueConfigurationOptionsProxy configurationOptionsProxy;
    private final QueueServiceClientWrapper queueServiceClientWrapper;
    private final QueueServiceOperations queueServiceOperations;

    public QueueProducer(final Endpoint endpoint) {
        super(endpoint);
        this.queueServiceClientWrapper = new QueueServiceClientWrapper(getEndpoint().getQueueServiceClient());
        this.queueServiceOperations = new QueueServiceOperations(getEndpoint().getConfiguration(), queueServiceClientWrapper);
        this.configurationOptionsProxy = new QueueConfigurationOptionsProxy(getEndpoint().getConfiguration());
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        QueueOperationDefinition operation = determineOperation(exchange);

        if (ObjectHelper.isEmpty(operation)) {
            operation = QueueOperationDefinition.sendMessage;
        }

        switch (operation) {
            // service operations
            case listQueues:
                setResponse(exchange, queueServiceOperations.listQueues(exchange));
                break;
            // queue operations
            case createQueue:
                setResponse(exchange, getQueueOperations(exchange).createQueue(exchange));
                break;
            case deleteQueue:
                setResponse(exchange, getQueueOperations(exchange).deleteQueue(exchange));
                break;
            case clearQueue:
                setResponse(exchange, getQueueOperations(exchange).clearQueue(exchange));
                break;
            case sendMessage:
                setResponse(exchange, getQueueOperations(exchange).sendMessage(exchange));
                break;
            case deleteMessage:
                setResponse(exchange, getQueueOperations(exchange).deleteMessage(exchange));
                break;
            case peekMessages:
                setResponse(exchange, getQueueOperations(exchange).peekMessages(exchange));
                break;
            case updateMessage:
                setResponse(exchange, getQueueOperations(exchange).updateMessage(exchange));
                break;
            case receiveMessages:
                setResponse(exchange, getQueueOperations(exchange).receiveMessages(exchange));
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    @Override
    public QueueEndpoint getEndpoint() {
        return (QueueEndpoint) super.getEndpoint();
    }

    private void setResponse(final Exchange exchange, final QueueOperationResponse response) {
        exchange.getMessage().setBody(response.getBody());
        exchange.getMessage().getHeaders().putAll(response.getHeaders());
    }

    private QueueOperationDefinition determineOperation(final Exchange exchange) {
        return configurationOptionsProxy.getQueueOperation(exchange);
    }

    private QueueOperations getQueueOperations(final Exchange exchange) {
        return new QueueOperations(
                getEndpoint().getConfiguration(),
                queueServiceClientWrapper.getQueueClientWrapper(determineQueueName(exchange)));
    }

    private String determineQueueName(final Exchange exchange) {
        final String queueName = configurationOptionsProxy.getQueueName(exchange);

        if (ObjectHelper.isEmpty(queueName)) {
            throw new IllegalArgumentException("Queue name must be specified");
        }
        return queueName;
    }
}
