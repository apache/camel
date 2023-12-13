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
package org.apache.camel.component.azure.eventhubs;

import java.util.function.Consumer;

import com.azure.identity.IntelliJCredentialBuilder;
import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EventProcessorTest {

    @Test
    public void testCreateEventProcessorWithNonValidOptions() {
        final EventHubsConfiguration configuration = new EventHubsConfiguration();
        final Consumer<EventContext> onEvent = event -> {
        };
        final Consumer<ErrorContext> onError = error -> {
        };

        assertThrows(IllegalArgumentException.class,
                () -> EventHubsClientFactory.createEventProcessorClient(configuration, onEvent, onError));

        configuration.setBlobContainerName("testContainer");
        assertThrows(IllegalArgumentException.class,
                () -> EventHubsClientFactory.createEventProcessorClient(configuration, onEvent, onError));

        configuration.setBlobAccountName("testAccount");
        assertThrows(IllegalArgumentException.class,
                () -> EventHubsClientFactory.createEventProcessorClient(configuration, onEvent, onError));

        configuration.setBlobAccessKey("testAccess");
        assertNotNull(EventHubsClientFactory.createEventProcessorClient(configuration, onEvent, onError));

        configuration.setBlobContainerName(null);
        assertThrows(IllegalArgumentException.class,
                () -> EventHubsClientFactory.createEventProcessorClient(configuration, onEvent, onError));
    }

    @Test
    public void testCreateEventProcessorWithTokenCredential() {
        final EventHubsConfiguration configuration = new EventHubsConfiguration();
        final Consumer<EventContext> onEvent = event -> {
        };
        final Consumer<ErrorContext> onError = error -> {
        };

        configuration.setBlobContainerName("testContainer");
        configuration.setBlobAccountName("testAccount");
        configuration.setBlobAccessKey("testAccess");
        assertNotNull(EventHubsClientFactory.createEventProcessorClient(configuration, onEvent, onError));

        configuration.setTokenCredential(new IntelliJCredentialBuilder().tenantId("tenantId").build());
        configuration.setCredentialType(CredentialType.TOKEN_CREDENTIAL);
        assertThrows(IllegalArgumentException.class,
                () -> EventHubsClientFactory.createEventProcessorClient(configuration, onEvent, onError));

        configuration.setNamespace("namespace");
        assertThrows(IllegalArgumentException.class,
                () -> EventHubsClientFactory.createEventProcessorClient(configuration, onEvent, onError));

        configuration.setEventHubName("eventHubName");
        assertNotNull(EventHubsClientFactory.createEventProcessorClient(configuration, onEvent, onError));
    }

}
