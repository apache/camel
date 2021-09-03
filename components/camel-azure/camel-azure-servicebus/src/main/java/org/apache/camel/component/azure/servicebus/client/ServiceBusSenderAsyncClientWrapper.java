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
package org.apache.camel.component.azure.servicebus.client;

import java.time.OffsetDateTime;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusMessageBatch;
import com.azure.messaging.servicebus.ServiceBusSenderAsyncClient;
import com.azure.messaging.servicebus.ServiceBusTransactionContext;
import com.azure.messaging.servicebus.models.CreateMessageBatchOptions;
import org.apache.camel.util.ObjectHelper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ServiceBusSenderAsyncClientWrapper {

    private final ServiceBusSenderAsyncClient client;

    public ServiceBusSenderAsyncClientWrapper(final ServiceBusSenderAsyncClient client) {
        ObjectHelper.isNotEmpty(client);

        this.client = client;
    }

    public String getFullyQualifiedNamespace() {
        return client.getFullyQualifiedNamespace();
    }

    public String getEntityPath() {
        return client.getEntityPath();
    }

    public Mono<Void> sendMessage(ServiceBusMessage message) {
        return client.sendMessage(message);
    }

    public Mono<Void> sendMessage(
            ServiceBusMessage message,
            ServiceBusTransactionContext transactionContext) {
        return client.sendMessage(message, transactionContext);
    }

    public Mono<Void> sendMessages(
            Iterable<ServiceBusMessage> messages,
            ServiceBusTransactionContext transactionContext) {
        return client.sendMessages(messages, transactionContext);
    }

    public Mono<Void> sendMessages(Iterable<ServiceBusMessage> messages) {
        return client.sendMessages(messages);
    }

    public Mono<Void> sendMessages(ServiceBusMessageBatch batch) {
        return client.sendMessages(batch);
    }

    public Mono<Void> sendMessages(
            ServiceBusMessageBatch batch,
            ServiceBusTransactionContext transactionContext) {
        return client.sendMessages(batch, transactionContext);
    }

    public Mono<ServiceBusMessageBatch> createMessageBatch() {
        return client.createMessageBatch();
    }

    public Mono<ServiceBusMessageBatch> createMessageBatch(
            CreateMessageBatchOptions options) {
        return client.createMessageBatch(options);
    }

    public Mono<Long> scheduleMessage(
            ServiceBusMessage message, OffsetDateTime scheduledEnqueueTime,
            ServiceBusTransactionContext transactionContext) {
        return client.scheduleMessage(message, scheduledEnqueueTime, transactionContext);
    }

    public Mono<Long> scheduleMessage(ServiceBusMessage message, OffsetDateTime scheduledEnqueueTime) {
        return client.scheduleMessage(message, scheduledEnqueueTime);
    }

    public Flux<Long> scheduleMessages(
            Iterable<ServiceBusMessage> messages,
            OffsetDateTime scheduledEnqueueTime) {
        return client.scheduleMessages(messages, scheduledEnqueueTime);
    }

    public Flux<Long> scheduleMessages(
            Iterable<ServiceBusMessage> messages,
            OffsetDateTime scheduledEnqueueTime,
            ServiceBusTransactionContext transactionContext) {
        return client.scheduleMessages(messages, scheduledEnqueueTime, transactionContext);
    }

    public Mono<Void> cancelScheduledMessage(long sequenceNumber) {
        return client.cancelScheduledMessage(sequenceNumber);
    }

    public Mono<Void> cancelScheduledMessages(Iterable<Long> sequenceNumbers) {
        return client.cancelScheduledMessages(sequenceNumbers);
    }

    public Mono<ServiceBusTransactionContext> createTransaction() {
        return client.createTransaction();
    }

    public Mono<Void> commitTransaction(ServiceBusTransactionContext transactionContext) {
        return client.commitTransaction(transactionContext);
    }

    public Mono<Void> rollbackTransaction(ServiceBusTransactionContext transactionContext) {
        return client.rollbackTransaction(transactionContext);
    }

    public void close() {
        client.close();
    }
}
