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
import java.util.Map;

import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.models.PeekedMessageItem;
import com.azure.storage.queue.models.QueueMessageItem;
import com.azure.storage.queue.models.SendMessageResult;
import com.azure.storage.queue.models.UpdateMessageResult;
import org.apache.camel.util.ObjectHelper;

public class QueueClientWrapper {

    private QueueClient client;

    public QueueClientWrapper(final QueueClient client) {
        ObjectHelper.notNull(client, "client cannot be null");

        this.client = client;
    }

    public Response<Void> create(Map<String, String> metadata, Duration timeout) {
        return client.createWithResponse(metadata, timeout, Context.NONE);
    }

    public Response<Void> delete(Duration timeout) {
        return client.deleteWithResponse(timeout, Context.NONE);
    }

    public Response<Void> clearMessages(Duration timeout) {
        return client.clearMessagesWithResponse(timeout, Context.NONE);
    }

    public Response<SendMessageResult> sendMessage(
            String messageText, Duration visibilityTimeout, Duration timeToLive, Duration timeout) {
        return client.sendMessageWithResponse(messageText, visibilityTimeout, timeToLive, timeout, Context.NONE);
    }

    public Response<Void> deleteMessage(String messageId, String popReceipt, Duration timeout) {
        return client.deleteMessageWithResponse(messageId, popReceipt, timeout, Context.NONE);
    }

    public List<QueueMessageItem> receiveMessages(Integer maxMessages, Duration visibilityTimeout, Duration timeout) {
        return client.receiveMessages(maxMessages, visibilityTimeout, timeout, Context.NONE).stream()
                .toList();
    }

    public List<PeekedMessageItem> peekMessages(Integer maxMessages, Duration timeout) {
        return client.peekMessages(maxMessages, timeout, Context.NONE).stream().toList();
    }

    public Response<UpdateMessageResult> updateMessage(
            String messageId, String popReceipt, String messageText, Duration visibilityTimeout, Duration timeout) {
        return client.updateMessageWithResponse(messageId, popReceipt, messageText, visibilityTimeout, timeout, Context.NONE);
    }
}
