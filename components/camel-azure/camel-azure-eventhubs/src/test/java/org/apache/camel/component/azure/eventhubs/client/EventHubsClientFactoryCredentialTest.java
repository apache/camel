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
package org.apache.camel.component.azure.eventhubs.client;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import org.apache.camel.component.azure.common.CredentialType;
import org.apache.camel.component.azure.eventhubs.EventHubsConfiguration;
import org.apache.camel.component.azure.eventhubs.EventHubsEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * A DefaultAzureCredential set as tokenCredential is the credential of the clients. With no credentialType, the
 * component chooses AZURE_IDENTITY for it.
 */
class EventHubsClientFactoryCredentialTest extends CamelTestSupport {

    @Test
    void defaultAzureCredentialAsTokenCredentialIsUsed() {
        DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().managedIdentityClientId("my-id").build();
        context.getRegistry().bind("cred", credential);

        // no credentialType: the component sets AZURE_IDENTITY for a DefaultAzureCredential
        EventHubsConfiguration configuration = context.getEndpoint(
                "azure-eventhubs:mynamespace/myhub?tokenCredential=#cred", EventHubsEndpoint.class).getConfiguration();
        assertEquals(CredentialType.AZURE_IDENTITY, configuration.getCredentialType());

        try (MockedConstruction<EventHubClientBuilder> builders
                = mockConstruction(EventHubClientBuilder.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            EventHubsClientFactory.createEventHubProducerAsyncClient(configuration);
            EventHubsClientFactory.createEventHubConsumerAsyncClient(configuration);

            assertEquals(2, builders.constructed().size());
            verify(builders.constructed().get(0)).credential(same(credential));
            verify(builders.constructed().get(1)).credential(same(credential));
        }
    }

    @Test
    void azureIdentityWithoutTokenCredentialBuildsOne() {
        EventHubsConfiguration configuration = context.getEndpoint(
                "azure-eventhubs:mynamespace/myhub?credentialType=AZURE_IDENTITY", EventHubsEndpoint.class)
                .getConfiguration();

        try (MockedConstruction<EventHubClientBuilder> builders
                = mockConstruction(EventHubClientBuilder.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            EventHubsClientFactory.createEventHubProducerAsyncClient(configuration);

            verify(builders.constructed().get(0)).credential(any(DefaultAzureCredential.class));
        }
    }
}
