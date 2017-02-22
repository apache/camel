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

import com.microsoft.azure.storage.StorageException;
import org.apache.camel.Exchange;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Consumer of the queue content from the Azure Queue Service
 */
public class QueueServiceConsumer extends ScheduledPollConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(QueueServiceConsumer.class);
    
    public QueueServiceConsumer(QueueServiceEndpoint endpoint, Processor processor) throws NoFactoryAvailableException {
        super(endpoint, processor);
    }
    
    @Override
    protected int poll() throws Exception {
        Exchange exchange = super.getEndpoint().createExchange();
        try {
            LOG.trace("Retrieving a message");
            retrieveMessage(exchange);
            super.getAsyncProcessor().process(exchange);
            return 1;
        } catch (StorageException ex) {
            if (404 == ex.getHttpStatusCode()) {
                return 0;
            } else {
                throw ex;
            }
        }
    }
    
    private void retrieveMessage(Exchange exchange) throws Exception {
        //TODO: Support the batch processing if needed, given that it is possible
        // to retrieve more than 1 message in one go, similarly to camel-aws/s3 consumer. 
        QueueServiceUtil.retrieveMessage(exchange, getConfiguration());
        
    }
        
    protected QueueServiceConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public QueueServiceEndpoint getEndpoint() {
        return (QueueServiceEndpoint) super.getEndpoint();
    }

}