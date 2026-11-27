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

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import org.apache.camel.component.azure.common.CredentialType;
import org.apache.camel.component.azure.servicebus.ServiceBusConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * A DefaultAzureCredential set as tokenCredential is the credential of the client with AZURE_IDENTITY.
 */
class ServiceBusClientFactoryCredentialTest {

    private static final String NAMESPACE = "mynamespace.servicebus.windows.net";

    @Test
    void azureIdentityUsesTheTokenCredential() {
        DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().managedIdentityClientId("my-id").build();
        ServiceBusConfiguration configuration = configuration();
        configuration.setTokenCredential(credential);

        ServiceBusClientBuilder builder = buildSenderClient(configuration);

        verify(builder).credential(eq(NAMESPACE), same(credential));
    }

    @Test
    void azureIdentityWithoutTokenCredentialBuildsOne() {
        ServiceBusClientBuilder builder = buildSenderClient(configuration());

        verify(builder).credential(eq(NAMESPACE), any(DefaultAzureCredential.class));
    }

    private static ServiceBusConfiguration configuration() {
        ServiceBusConfiguration configuration = new ServiceBusConfiguration();
        configuration.setCredentialType(CredentialType.AZURE_IDENTITY);
        configuration.setFullyQualifiedNamespace(NAMESPACE);
        configuration.setTopicOrQueueName("myqueue");
        return configuration;
    }

    private static ServiceBusClientBuilder buildSenderClient(ServiceBusConfiguration configuration) {
        ServiceBusClientBuilder.ServiceBusSenderClientBuilder sender
                = mock(ServiceBusClientBuilder.ServiceBusSenderClientBuilder.class, RETURNS_SELF);
        try (MockedConstruction<ServiceBusClientBuilder> builders = mockConstruction(ServiceBusClientBuilder.class,
                withSettings().defaultAnswer(RETURNS_SELF), (mock, context) -> when(mock.sender()).thenReturn(sender))) {
            new ServiceBusClientFactory().createServiceBusSenderClient(configuration);

            assertEquals(1, builders.constructed().size());
            return builders.constructed().get(0);
        }
    }
}
