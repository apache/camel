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
package org.apache.camel.component.azure.blob;

import com.microsoft.azure.storage.StorageException;
import org.apache.camel.Exchange;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Consumer of the blob content from the Azure Blob Service
 */
// Extending DefaultConsumer is simpler if the blob must exist before this consumer is started,
// polling makes it easier to get the consumer working if no blob exists yet.
public class BlobServiceConsumer extends ScheduledPollConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(BlobServiceConsumer.class);
    
    public BlobServiceConsumer(BlobServiceEndpoint endpoint, Processor processor) throws NoFactoryAvailableException {
        super(endpoint, processor);
    }
    
    @Override
    protected int poll() throws Exception {
        Exchange exchange = super.getEndpoint().createExchange();
        try {
            LOG.trace("Getting the blob content");
            getBlob(exchange);
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
    
    private void getBlob(Exchange exchange) throws Exception {
        BlobServiceUtil.getBlob(exchange, getConfiguration());
    }
        
    protected BlobServiceConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public BlobServiceEndpoint getEndpoint() {
        return (BlobServiceEndpoint) super.getEndpoint();
    }

}