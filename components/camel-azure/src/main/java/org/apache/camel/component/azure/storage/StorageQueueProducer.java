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
package org.apache.camel.component.azure.storage;

import com.microsoft.azure.storage.queue.CloudQueue;
import com.microsoft.azure.storage.queue.CloudQueueClient;
import com.microsoft.azure.storage.queue.CloudQueueMessage;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Azure producer.
 */
public class StorageQueueProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(StorageQueueProducer.class);
    private StorageQueueEndpoint endpoint;

    public StorageQueueProducer(StorageQueueEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        String message = exchange.getIn().getBody(String.class);

        CloudQueueClient client = this.endpoint.getConfiguration().getQueueClient();
        CloudQueue queue = client.getQueueReference(this.endpoint.getConfiguration().getResource());

        queue.createIfNotExists();
        queue.addMessage(new CloudQueueMessage(message));
    }
}
