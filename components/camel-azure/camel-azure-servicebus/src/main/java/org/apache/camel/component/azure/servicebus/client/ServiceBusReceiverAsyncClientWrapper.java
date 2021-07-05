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

import java.time.Duration;
import java.time.OffsetDateTime;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverAsyncClient;
import com.azure.messaging.servicebus.ServiceBusTransactionContext;
import com.azure.messaging.servicebus.models.AbandonOptions;
import com.azure.messaging.servicebus.models.CompleteOptions;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.azure.messaging.servicebus.models.DeferOptions;
import org.apache.camel.util.ObjectHelper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ServiceBusReceiverAsyncClientWrapper {

    private ServiceBusReceiverAsyncClient client;

    public ServiceBusReceiverAsyncClientWrapper(final ServiceBusReceiverAsyncClient client) {
        ObjectHelper.isNotEmpty(client);

        this.client = client;
    }

    public String getFullyQualifiedNamespace() {
        return client.getFullyQualifiedNamespace();
    }

    public String getEntityPath() {
        return client.getEntityPath();
    }

    public Mono<Void> abandon(ServiceBusReceivedMessage message) {
        return client.abandon(message);
    }

    public Mono<Void> abandon(
            ServiceBusReceivedMessage message,
            AbandonOptions options) {
        return client.abandon(message, options);
    }

    public Mono<Void> complete(ServiceBusReceivedMessage message) {
        return client.complete(message);
    }

    public Mono<Void> complete(
            ServiceBusReceivedMessage message,
            CompleteOptions options) {
        return client.complete(message, options);
    }

    public Mono<Void> defer(ServiceBusReceivedMessage message) {
        return client.defer(message);
    }

    public Mono<Void> defer(
            ServiceBusReceivedMessage message,
            DeferOptions options) {
        return client.defer(message, options);
    }

    public Mono<Void> deadLetter(ServiceBusReceivedMessage message) {
        return client.deadLetter(message);
    }

    public Mono<Void> deadLetter(
            ServiceBusReceivedMessage message,
            DeadLetterOptions options) {
        return client.deadLetter(message, options);
    }

    public Mono<byte[]> getSessionState() {
        return client.getSessionState();
    }

    public Mono<ServiceBusReceivedMessage> peekMessage() {
        return client.peekMessage();
    }

    public Mono<ServiceBusReceivedMessage> peekMessage(long sequenceNumber) {
        return client.peekMessage(sequenceNumber);
    }

    public Flux<ServiceBusReceivedMessage> peekMessages(int maxMessages) {
        return client.peekMessages(maxMessages);
    }

    public Flux<ServiceBusReceivedMessage> peekMessages(int maxMessages, long sequenceNumber) {
        return client.peekMessages(maxMessages, sequenceNumber);
    }

    public Flux<ServiceBusReceivedMessage> receiveMessages() {
        return client.receiveMessages();
    }

    public Mono<ServiceBusReceivedMessage> receiveDeferredMessage(long sequenceNumber) {
        return client.receiveDeferredMessage(sequenceNumber);
    }

    public Flux<ServiceBusReceivedMessage> receiveDeferredMessages(Iterable<Long> sequenceNumbers) {
        return client.receiveDeferredMessages(sequenceNumbers);
    }

    public Mono<OffsetDateTime> renewMessageLock(ServiceBusReceivedMessage message) {
        return client.renewMessageLock(message);
    }

    public Mono<Void> renewMessageLock(ServiceBusReceivedMessage message, Duration maxLockRenewalDuration) {
        return client.renewMessageLock(message, maxLockRenewalDuration);
    }

    public Mono<OffsetDateTime> renewSessionLock() {
        return client.renewSessionLock();
    }

    public Mono<Void> renewSessionLock(Duration maxLockRenewalDuration) {
        return client.renewSessionLock(maxLockRenewalDuration);
    }

    public Mono<Void> setSessionState(byte[] sessionState) {
        return client.setSessionState(sessionState);
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
