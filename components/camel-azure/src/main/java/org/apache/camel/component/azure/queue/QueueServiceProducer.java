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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.blob.BlobServiceConstants;
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
            operation = QueueServiceOperations.getMessage;
        } else {
            switch (operation) {
            case getMessage:
                getMessage(exchange);
                break;
            case putMessage:
                putMessage(exchange);
                break;    
            default:
                throw new IllegalArgumentException("Unsupported operation");
            }
        }
             
    }
    
    private void getMessage(Exchange exchange) {
        LOG.trace("Getting the message from the queue [{}] from exchange [{}]...", 
                  getConfiguration().getQueueName(), exchange);
        throw new UnsupportedOperationException();    
    }
    private void putMessage(Exchange exchange) {
        LOG.trace("Putting the message into the queue [{}] from exchange [{}]...", 
                  getConfiguration().getQueueName(), exchange);
        throw new UnsupportedOperationException();    
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
