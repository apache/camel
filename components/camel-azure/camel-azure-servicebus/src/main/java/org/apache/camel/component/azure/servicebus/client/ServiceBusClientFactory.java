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

import com.azure.core.credential.TokenCredential;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceiverAsyncClient;
import com.azure.messaging.servicebus.ServiceBusSenderAsyncClient;
import org.apache.camel.component.azure.servicebus.ServiceBusConfiguration;
import org.apache.camel.component.azure.servicebus.ServiceBusType;
import org.apache.camel.util.ObjectHelper;

public final class ServiceBusClientFactory {

    private ServiceBusClientFactory() {
    }

    public static ServiceBusSenderAsyncClient createServiceBusSenderAsyncClient(final ServiceBusConfiguration configuration) {
        return createBaseServiceBusSenderClient(createBaseServiceBusClient(configuration), configuration)
                .buildAsyncClient();
    }

    public static ServiceBusReceiverAsyncClient createServiceBusReceiverAsyncClient(
            final ServiceBusConfiguration configuration) {
        return createBaseServiceBusReceiverClient(createBaseServiceBusClient(configuration), configuration)
                .prefetchCount(configuration.getPrefetchCount())
                .receiveMode(configuration.getServiceBusReceiveMode())
                .subQueue(configuration.getSubQueue())
                .maxAutoLockRenewDuration(configuration.getMaxAutoLockRenewDuration())
                .subscriptionName(configuration.getSubscriptionName())
                .buildAsyncClient();
    }

    private static ServiceBusClientBuilder createBaseServiceBusClient(final ServiceBusConfiguration configuration) {
        ServiceBusClientBuilder builder = new ServiceBusClientBuilder()
                .transportType(configuration.getAmqpTransportType())
                .clientOptions(configuration.getClientOptions())
                .retryOptions(configuration.getAmqpRetryOptions())
                .proxyOptions(configuration.getProxyOptions());

        String fullyQualifiedNamespace = configuration.getFullyQualifiedNamespace();
        TokenCredential credential = configuration.getTokenCredential();

        // If the FQNS and credential are available, use those to connect
        if (ObjectHelper.isNotEmpty(fullyQualifiedNamespace) && ObjectHelper.isNotEmpty(credential)) {
            builder.credential(fullyQualifiedNamespace, credential);
        } else {
            builder.connectionString(configuration.getConnectionString());
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

    private static ServiceBusClientBuilder.ServiceBusReceiverClientBuilder createBaseServiceBusReceiverClient(
            final ServiceBusClientBuilder busClientBuilder, final ServiceBusConfiguration configuration) {
        final ServiceBusClientBuilder.ServiceBusReceiverClientBuilder receiverClientBuilder = busClientBuilder.receiver();

        // We handle auto-complete in the consumer, since we have no way to propagate errors back to the reactive
        // pipeline messages are published on so the message would be completed even if an error occurs during Exchange
        // processing.
        receiverClientBuilder.disableAutoComplete();

        if (configuration.getServiceBusType() == ServiceBusType.queue) {
            return receiverClientBuilder.queueName(configuration.getTopicOrQueueName());
        } else {
            return receiverClientBuilder.topicName(configuration.getTopicOrQueueName());
        }
    }
}
