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

import java.util.function.Consumer;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.*;
import org.apache.camel.component.azure.servicebus.CredentialType;
import org.apache.camel.component.azure.servicebus.ServiceBusConfiguration;
import org.apache.camel.component.azure.servicebus.ServiceBusType;

public final class ServiceBusClientFactory {

    private static ServiceBusClientBuilder createBaseServiceBusClient(final ServiceBusConfiguration configuration) {
        ServiceBusClientBuilder builder = new ServiceBusClientBuilder()
                .transportType(configuration.getAmqpTransportType())
                .clientOptions(configuration.getClientOptions())
                .retryOptions(configuration.getAmqpRetryOptions())
                .proxyOptions(configuration.getProxyOptions());

        String fullyQualifiedNamespace = configuration.getFullyQualifiedNamespace();
        TokenCredential credential = configuration.getTokenCredential();

        CredentialType type = configuration.getCredentialType();
        if (type == null) {
            type = CredentialType.CONNECTION_STRING;
        }
        switch (type) {
            case CONNECTION_STRING -> builder.connectionString(configuration.getConnectionString());
            case TOKEN_CREDENTIAL -> builder.credential(fullyQualifiedNamespace, credential);
            case AZURE_IDENTITY -> builder.credential(fullyQualifiedNamespace, new DefaultAzureCredentialBuilder().build());
        }

        return builder;
    }

    private static ServiceBusClientBuilder.ServiceBusSenderClientBuilder createBaseServiceBusSenderClient(
            final ServiceBusClientBuilder busClientBuilder, final ServiceBusConfiguration configuration) {
        if (configuration.getServiceBusType() == ServiceBusType.queue) {
            return busClientBuilder.sender()
                    .queueName(configuration.getTopicOrQueueName());
        } else {
            return busClientBuilder.sender()
                    .topicName(configuration.getTopicOrQueueName());
        }
    }

    private static ServiceBusClientBuilder.ServiceBusProcessorClientBuilder createBaseServiceBusProcessorClient(
            final ServiceBusClientBuilder busClientBuilder, final ServiceBusConfiguration configuration) {
        final ServiceBusClientBuilder.ServiceBusProcessorClientBuilder processorClientBuilder = busClientBuilder.processor();

        // We handle auto-complete in the consumer, since we have no way to propagate errors back to the reactive
        // pipeline messages are published on so the message would be completed even if an error occurs during Exchange
        // processing.
        processorClientBuilder.disableAutoComplete();

        switch (configuration.getServiceBusType()) {
            case queue -> processorClientBuilder.queueName(configuration.getTopicOrQueueName());
            case topic -> processorClientBuilder.topicName(configuration.getTopicOrQueueName());
        }

        return processorClientBuilder;
    }

    private static ServiceBusClientBuilder.ServiceBusSessionProcessorClientBuilder createBaseServiceBusSessionProcessorClient(
            final ServiceBusClientBuilder busClientBuilder, final ServiceBusConfiguration configuration) {
        final ServiceBusClientBuilder.ServiceBusSessionProcessorClientBuilder processorClientBuilder
                = busClientBuilder.sessionProcessor();

        // We handle auto-complete in the consumer, since we have no way to propagate errors back to the reactive
        // pipeline messages are published on so the message would be completed even if an error occurs during Exchange
        // processing.
        processorClientBuilder.disableAutoComplete();

        switch (configuration.getServiceBusType()) {
            case queue -> processorClientBuilder.queueName(configuration.getTopicOrQueueName());
            case topic -> processorClientBuilder.topicName(configuration.getTopicOrQueueName());
        }

        return processorClientBuilder;
    }

    public ServiceBusSenderClient createServiceBusSenderClient(final ServiceBusConfiguration configuration) {
        return createBaseServiceBusSenderClient(createBaseServiceBusClient(configuration), configuration)
                .buildClient();
    }

    public ServiceBusProcessorClient createServiceBusProcessorClient(
            ServiceBusConfiguration configuration, Consumer<ServiceBusReceivedMessageContext> processMessage,
            Consumer<ServiceBusErrorContext> processError) {
        ServiceBusClientBuilder.ServiceBusProcessorClientBuilder clientBuilder
                = createBaseServiceBusProcessorClient(createBaseServiceBusClient(configuration), configuration);

        clientBuilder
                .subscriptionName(configuration.getSubscriptionName())
                .receiveMode(configuration.getServiceBusReceiveMode())
                .maxAutoLockRenewDuration(configuration.getMaxAutoLockRenewDuration())
                .prefetchCount(configuration.getPrefetchCount())
                .subQueue(configuration.getSubQueue())
                .maxConcurrentCalls(configuration.getMaxConcurrentCalls())
                .processMessage(processMessage)
                .processError(processError);

        return clientBuilder.buildProcessorClient();
    }

    public ServiceBusProcessorClient createServiceBusSessionProcessorClient(
            ServiceBusConfiguration configuration, Consumer<ServiceBusReceivedMessageContext> processMessage,
            Consumer<ServiceBusErrorContext> processError) {

        ServiceBusClientBuilder.ServiceBusSessionProcessorClientBuilder clientBuilder
                = createBaseServiceBusSessionProcessorClient(createBaseServiceBusClient(configuration), configuration);

        clientBuilder
                .subscriptionName(configuration.getSubscriptionName())
                .receiveMode(configuration.getServiceBusReceiveMode())
                .maxAutoLockRenewDuration(configuration.getMaxAutoLockRenewDuration())
                .prefetchCount(configuration.getPrefetchCount())
                .subQueue(configuration.getSubQueue())
                .maxConcurrentCalls(configuration.getMaxConcurrentCalls())
                .processMessage(processMessage)
                .processError(processError);

        return clientBuilder.buildProcessorClient();
    }
}
