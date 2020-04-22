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
import com.azure.storage.queue.models.QueueStorageException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.azure.storage.queue.client.QueueClientWrapper;
import org.apache.camel.component.azure.storage.queue.operations.QueueOperationResponse;
import org.apache.camel.component.azure.storage.queue.operations.QueueOperations;
import org.apache.camel.support.ScheduledPollConsumer;

public class QueueConsumer extends ScheduledPollConsumer {

    public QueueConsumer(final QueueEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        final String queueName = getEndpoint().getConfiguration().getQueueName();
        final QueueServiceClient serviceClient = getEndpoint().getQueueServiceClient();
        final QueueClientWrapper clientWrapper = new QueueClientWrapper(serviceClient.getQueueClient(queueName));
        final QueueOperations operations = new QueueOperations(getEndpoint().getConfiguration(), clientWrapper);
        final Exchange exchange = getEndpoint().createExchange();

        try {
            final QueueOperationResponse response = operations.receiveMessages(null);
            getEndpoint().setResponseOnExchange(response, exchange);

            getAsyncProcessor().process(exchange);
            return 1;
        } catch (QueueStorageException ex) {
            if (404 == ex.getStatusCode()) {
                return 0;
            } else {
                throw ex;
            }
        }
    }

    @Override
    public QueueEndpoint getEndpoint() {
        return (QueueEndpoint) super.getEndpoint();
    }
}
