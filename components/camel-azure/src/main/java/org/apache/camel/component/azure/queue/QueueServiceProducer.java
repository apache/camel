/**
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

import java.util.EnumSet;

import com.microsoft.azure.storage.queue.CloudQueue;
import com.microsoft.azure.storage.queue.CloudQueueMessage;
import com.microsoft.azure.storage.queue.MessageUpdateFields;
import com.microsoft.azure.storage.queue.QueueListingDetails;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.blob.BlobServiceConstants;
import org.apache.camel.component.azure.common.ExchangeUtil;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Azure Storage Queue Service
 */
public class QueueServiceProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(QueueServiceProducer.class);

    public QueueServiceProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        QueueServiceOperations operation = determineOperation(exchange);
        if (ObjectHelper.isEmpty(operation)) {
            operation = QueueServiceOperations.listQueues;
        } else {
            switch (operation) {
            case retrieveMessage:
                retrieveMessage(exchange);
                break;
            case peekMessage:
                peekMessage(exchange);
                break;    
            case createQueue:
                createQueue(exchange);
                break;
            case deleteQueue:
                deleteQueue(exchange);
                break;    
            case addMessage:
                addMessage(exchange);
                break;
            case updateMessage:
                updateMessage(exchange);
                break;
            case deleteMessage:
                deleteMessage(exchange);
                break;
            case listQueues:
                listQueues(exchange);
                break;    
            default:
                throw new IllegalArgumentException("Unsupported operation");
            }
        }
             
    }
    
    private void listQueues(Exchange exchange) throws Exception {
        CloudQueue client = QueueServiceUtil.createQueueClient(getConfiguration());
        QueueServiceRequestOptions opts = QueueServiceUtil.getRequestOptions(exchange);
        QueueListingDetails details = (QueueListingDetails)exchange.getIn().getHeader(QueueServiceConstants.QUEUE_LISTING_DETAILS);
        if (details == null) {
            details = QueueListingDetails.ALL;
        }
        Iterable<CloudQueue> list = client.getServiceClient().listQueues(
            getConfiguration().getQueuePrefix(), details, 
            opts.getRequestOpts(), opts.getOpContext());
        ExchangeUtil.getMessageForResponse(exchange).setBody(list);
    }
    
    private void createQueue(Exchange exchange) throws Exception {
        CloudQueue client = QueueServiceUtil.createQueueClient(getConfiguration());
        QueueServiceRequestOptions opts = QueueServiceUtil.getRequestOptions(exchange);
        doCreateQueue(client, opts, exchange);
    }
    
    private void doCreateQueue(CloudQueue client, QueueServiceRequestOptions opts, Exchange exchange) throws Exception {
        LOG.trace("Creating the queue [{}] from exchange [{}]...", 
                  getConfiguration().getQueueName(), exchange);
        client.createIfNotExists(opts.getRequestOpts(), opts.getOpContext());
        ExchangeUtil.getMessageForResponse(exchange)
            .setHeader(QueueServiceConstants.QUEUE_CREATED, Boolean.TRUE);
    }
    
    private void deleteQueue(Exchange exchange) throws Exception {
        LOG.trace("Deleting the queue [{}] from exchange [{}]...", 
                  getConfiguration().getQueueName(), exchange);
        CloudQueue client = QueueServiceUtil.createQueueClient(getConfiguration());
        QueueServiceRequestOptions opts = QueueServiceUtil.getRequestOptions(exchange);
        client.delete(opts.getRequestOpts(), opts.getOpContext());
    }
    
    private void addMessage(Exchange exchange) throws Exception {
        LOG.trace("Putting the message into the queue [{}] from exchange [{}]...", 
                  getConfiguration().getQueueName(), exchange);
        CloudQueue client = QueueServiceUtil.createQueueClient(getConfiguration());
        QueueServiceRequestOptions opts = QueueServiceUtil.getRequestOptions(exchange);
        
        Boolean queueCreated = exchange.getIn().getHeader(QueueServiceConstants.QUEUE_CREATED, 
                                                          Boolean.class);
        if (Boolean.TRUE != queueCreated) {
            doCreateQueue(client, opts, exchange);
        }
        
        CloudQueueMessage message = getCloudQueueMessage(exchange);
        client.addMessage(message, 
                          getConfiguration().getMessageTimeToLive(), 
                          getConfiguration().getMessageVisibilityDelay(),
                          opts.getRequestOpts(), opts.getOpContext());
    }
    private void updateMessage(Exchange exchange) throws Exception {
        CloudQueue client = QueueServiceUtil.createQueueClient(getConfiguration());
        QueueServiceRequestOptions opts = QueueServiceUtil.getRequestOptions(exchange);
        
        CloudQueueMessage message = getCloudQueueMessage(exchange);
        LOG.trace("Updating the message in the queue [{}] from exchange [{}]...", 
                  getConfiguration().getQueueName(), exchange);
        
        EnumSet<MessageUpdateFields> fields = null;
        Object fieldsObject = exchange.getIn().getHeader(QueueServiceConstants.MESSAGE_UPDATE_FIELDS);
        if (fieldsObject instanceof EnumSet) {
            @SuppressWarnings("unchecked")
            EnumSet<MessageUpdateFields> theFields = (EnumSet<MessageUpdateFields>)fieldsObject;
            fields = theFields;
        } else if (fieldsObject instanceof MessageUpdateFields) {
            fields = EnumSet.of((MessageUpdateFields)fieldsObject);
        }
        client.updateMessage(message, 
                          getConfiguration().getMessageVisibilityDelay(),
                          fields,
                          opts.getRequestOpts(), opts.getOpContext());
    }
    
    private void deleteMessage(Exchange exchange) throws Exception {
        LOG.trace("Deleting the message from the queue [{}] from exchange [{}]...", 
                  getConfiguration().getQueueName(), exchange);
        CloudQueue client = QueueServiceUtil.createQueueClient(getConfiguration());
        QueueServiceRequestOptions opts = QueueServiceUtil.getRequestOptions(exchange);
        CloudQueueMessage message = getCloudQueueMessage(exchange);
        client.deleteMessage(message, opts.getRequestOpts(), opts.getOpContext());
    }

    private void retrieveMessage(Exchange exchange) throws Exception {
        QueueServiceUtil.retrieveMessage(exchange, getConfiguration());
    }
    
    private void peekMessage(Exchange exchange) throws Exception {
        CloudQueue client = QueueServiceUtil.createQueueClient(getConfiguration());
        QueueServiceRequestOptions opts = QueueServiceUtil.getRequestOptions(exchange);  
        CloudQueueMessage message = client.peekMessage(opts.getRequestOpts(), opts.getOpContext());
        ExchangeUtil.getMessageForResponse(exchange).setBody(message);
    }
    
    
    private CloudQueueMessage getCloudQueueMessage(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getMandatoryBody();
        CloudQueueMessage message = null;
        if (body instanceof CloudQueueMessage) {
            message = (CloudQueueMessage)body;
        } else if (body instanceof String) {
            message = new CloudQueueMessage((String)body);
        }
        if (message == null) {
            throw new IllegalArgumentException("Unsupported queue message type:" + body.getClass().getName());
        }
        return message;
    }

    private QueueServiceOperations determineOperation(Exchange exchange) {
        QueueServiceOperations operation = exchange.getIn().getHeader(BlobServiceConstants.OPERATION, 
                                                                      QueueServiceOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected QueueServiceConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        return "StorageQueueProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
    }

    @Override
    public QueueServiceEndpoint getEndpoint() {
        return (QueueServiceEndpoint) super.getEndpoint();
    }
 
}
