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
package org.apache.camel.component.azure.servicebus.operations;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusTransactionContext;
import org.apache.camel.component.azure.servicebus.ServiceBusUtils;
import org.apache.camel.component.azure.servicebus.client.ServiceBusSenderAsyncClientWrapper;
import org.apache.camel.util.ObjectHelper;
import reactor.core.publisher.Mono;

public class ServiceBusSenderOperations {

    private final ServiceBusSenderAsyncClientWrapper client;

    public ServiceBusSenderOperations(ServiceBusSenderAsyncClientWrapper client) {
        ObjectHelper.notNull(client, "client");

        this.client = client;
    }

    public Mono<Void> sendMessages(
            final Object data,
            final ServiceBusTransactionContext context,
            final Map<String, Object> applicationProperties) {
        if (data instanceof Iterable) {
            return sendMessages((Iterable<Object>) data, context, applicationProperties);
        }

        return sendMessage(data, context, applicationProperties);
    }

    public Mono<List<Long>> scheduleMessages(
            final Object data,
            final OffsetDateTime scheduledEnqueueTime,
            final ServiceBusTransactionContext context,
            final Map<String, Object> applicationProperties) {
        if (ObjectHelper.isEmpty(scheduledEnqueueTime)) {
            throw new IllegalArgumentException("To schedule a message, you need to set scheduledEnqueueTime.");
        }

        if (data instanceof Iterable) {
            return scheduleMessages((Iterable<Object>) data, scheduledEnqueueTime, context, applicationProperties);
        }

        return scheduleMessage(data, scheduledEnqueueTime, context, applicationProperties);
    }

    private Mono<Void> sendMessages(
            final Iterable<Object> data,
            final ServiceBusTransactionContext context,
            final Map<String, Object> applicationProperties) {
        final Iterable<ServiceBusMessage> messages = ServiceBusUtils.createServiceBusMessages(data, applicationProperties);

        if (ObjectHelper.isEmpty(context)) {
            return client.sendMessages(messages);
        }

        return client.sendMessages(messages, context);
    }

    private Mono<Void> sendMessage(
            final Object data,
            final ServiceBusTransactionContext context,
            final Map<String, Object> applicationProperties) {
        final ServiceBusMessage message = ServiceBusUtils.createServiceBusMessage(data, applicationProperties);

        if (ObjectHelper.isEmpty(context)) {
            return client.sendMessage(message);
        }

        return client.sendMessage(message, context);
    }

    private Mono<List<Long>> scheduleMessage(
            final Object data,
            final OffsetDateTime scheduledEnqueueTime,
            final ServiceBusTransactionContext context,
            final Map<String, Object> applicationProperties) {
        final ServiceBusMessage message = ServiceBusUtils.createServiceBusMessage(data, applicationProperties);

        if (ObjectHelper.isEmpty(context)) {
            return client.scheduleMessage(message, scheduledEnqueueTime)
                    .map(Collections::singletonList);
        }

        return client.scheduleMessage(message, scheduledEnqueueTime, context)
                .map(Collections::singletonList);
    }

    private Mono<List<Long>> scheduleMessages(
            final Iterable<Object> data, final OffsetDateTime scheduledEnqueueTime,
            final ServiceBusTransactionContext context,
            final Map<String, Object> applicationProperties) {
        final Iterable<ServiceBusMessage> messages = ServiceBusUtils.createServiceBusMessages(data, applicationProperties);

        if (ObjectHelper.isEmpty(context)) {
            return client.scheduleMessages(messages, scheduledEnqueueTime)
                    .collectList();
        }

        return client.scheduleMessages(messages, scheduledEnqueueTime, context)
                .collectList();
    }
}
