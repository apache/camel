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
package org.apache.camel.component.azure.storage.queue.operations;

import java.time.Duration;

import com.azure.storage.queue.models.QueuesSegmentOptions;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.queue.QueueConfiguration;
import org.apache.camel.component.azure.storage.queue.QueueConfigurationOptionsProxy;
import org.apache.camel.component.azure.storage.queue.client.QueueServiceClientWrapper;
import org.apache.camel.util.ObjectHelper;

/**
 * All operations related to {@link com.azure.storage.queue.QueueServiceClient}. This is at the service level
 */
public class QueueServiceOperations {

    private final QueueConfigurationOptionsProxy configurationOptionsProxy;
    private final QueueServiceClientWrapper client;

    public QueueServiceOperations(final QueueConfiguration configuration, final QueueServiceClientWrapper client) {
        ObjectHelper.notNull(client, "client can not be null.");

        this.client = client;
        this.configurationOptionsProxy = new QueueConfigurationOptionsProxy(configuration);
    }

    public QueueOperationResponse listQueues(final Exchange exchange) {
        if (exchange == null) {
            return QueueOperationResponse.create(client.listQueues(null, null));
        }
        final QueuesSegmentOptions segmentOptions = configurationOptionsProxy.getQueuesSegmentOptions(exchange);
        final Duration timeout = configurationOptionsProxy.getTimeout(exchange);

        return QueueOperationResponse.create(client.listQueues(segmentOptions, timeout));
    }
}
