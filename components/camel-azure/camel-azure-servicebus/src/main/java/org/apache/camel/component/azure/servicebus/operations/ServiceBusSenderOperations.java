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
import java.util.stream.StreamSupport;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.ServiceBusTransactionContext;
import org.apache.camel.component.azure.servicebus.ServiceBusUtils;
import org.apache.camel.util.ObjectHelper;

public class ServiceBusSenderOperations {

    private final ServiceBusSenderClient client;

    public ServiceBusSenderOperations(ServiceBusSenderClient client) {
        ObjectHelper.notNull(client, "client");

        this.client = client;
    }

    public void sendMessages(
            final Object data,
            final ServiceBusTransactionContext context,
            final Map<String, Object> applicationProperties,
            final String correlationId) {
        if (data instanceof Iterable<?>) {
            sendMessages((Iterable<?>) data, context, applicationProperties, correlationId);
        } else {
            sendMessage(data, context, applicationProperties, correlationId);
        }
    }

    public List<Long> scheduleMessages(
            final Object data,
            final OffsetDateTime scheduledEnqueueTime,
            final ServiceBusTransactionContext context,
            final Map<String, Object> applicationProperties,
            final String correlationId) {
        if (ObjectHelper.isEmpty(scheduledEnqueueTime)) {
            throw new IllegalArgumentException("To schedule a message, you need to set scheduledEnqueueTime.");
        }

        if (data instanceof Iterable<?>) {
            return scheduleMessages((Iterable<?>) data, scheduledEnqueueTime, context, applicationProperties, correlationId);
        }

        return scheduleMessage(data, scheduledEnqueueTime, context, applicationProperties, correlationId);
    }

    private void sendMessages(
            final Iterable<?> data,
            final ServiceBusTransactionContext context,
            final Map<String, Object> applicationProperties,
            final String correlationId) {
        final Iterable<ServiceBusMessage> messages
                = ServiceBusUtils.createServiceBusMessages(data, applicationProperties, correlationId);

        if (ObjectHelper.isEmpty(context)) {
            client.sendMessages(messages);
        } else {
            client.sendMessages(messages, context);
        }
    }

    private void sendMessage(
            final Object data,
            final ServiceBusTransactionContext context,
            final Map<String, Object> applicationProperties,
            final String correlationId) {
        final ServiceBusMessage message = ServiceBusUtils.createServiceBusMessage(data, applicationProperties, correlationId);

        if (ObjectHelper.isEmpty(context)) {
            client.sendMessage(message);
        } else {
            client.sendMessage(message, context);
        }
    }

    private List<Long> scheduleMessage(
            final Object data,
            final OffsetDateTime scheduledEnqueueTime,
            final ServiceBusTransactionContext context,
            final Map<String, Object> applicationProperties,
            final String correlationId) {
        final ServiceBusMessage message = ServiceBusUtils.createServiceBusMessage(data, applicationProperties, correlationId);

        if (ObjectHelper.isEmpty(context)) {
            return Collections.singletonList(client.scheduleMessage(message, scheduledEnqueueTime));
        }

        return Collections.singletonList(client.scheduleMessage(message, scheduledEnqueueTime, context));
    }

    private List<Long> scheduleMessages(
            final Iterable<?> data, final OffsetDateTime scheduledEnqueueTime,
            final ServiceBusTransactionContext context,
            final Map<String, Object> applicationProperties,
            final String correlationId) {
        final Iterable<ServiceBusMessage> messages
                = ServiceBusUtils.createServiceBusMessages(data, applicationProperties, correlationId);

        if (ObjectHelper.isEmpty(context)) {
            return StreamSupport.stream(client.scheduleMessages(messages, scheduledEnqueueTime).spliterator(), false).toList();
        }

        return StreamSupport.stream(client.scheduleMessages(messages, scheduledEnqueueTime, context).spliterator(), false)
                .toList();
    }
}
