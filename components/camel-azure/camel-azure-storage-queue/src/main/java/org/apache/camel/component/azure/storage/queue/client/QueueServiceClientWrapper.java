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
package org.apache.camel.component.azure.storage.queue.client;

import java.time.Duration;
import java.util.List;

import com.azure.core.util.Context;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.models.QueueItem;
import com.azure.storage.queue.models.QueuesSegmentOptions;
import org.apache.camel.util.ObjectHelper;

public class QueueServiceClientWrapper {

    private final QueueServiceClient client;

    public QueueServiceClientWrapper(final QueueServiceClient client) {
        ObjectHelper.notNull(client, "client cannot be null");

        this.client = client;
    }

    public List<QueueItem> listQueues(QueuesSegmentOptions options, Duration timeout) {
        return client.listQueues(options, timeout, Context.NONE).stream().toList();
    }

    public QueueClientWrapper getQueueClientWrapper(final String queueName) {
        if (!ObjectHelper.isEmpty(queueName)) {
            return new QueueClientWrapper(client.getQueueClient(queueName));
        }
        throw new IllegalArgumentException("Cannot initialize a queue since no queue name was provided.");
    }
}
