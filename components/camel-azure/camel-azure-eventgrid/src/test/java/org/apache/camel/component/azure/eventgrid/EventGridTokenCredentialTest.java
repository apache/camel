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
package org.apache.camel.component.azure.eventgrid;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.eventgrid.EventGridPublisherClientBuilder;
import org.apache.camel.Producer;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * A DefaultAzureCredential set as tokenCredential is the credential of the publisher client.
 */
class EventGridTokenCredentialTest extends CamelTestSupport {

    private static final String TOPIC = "azure-eventgrid:https://mytopic.eventgrid.azure.net/api/events";

    @Test
    void defaultAzureCredentialIsUsed() throws Exception {
        DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().managedIdentityClientId("my-id").build();
        context.getRegistry().bind("cred", credential);

        EventGridEndpoint endpoint = context.getEndpoint(TOPIC + "?tokenCredential=#cred", EventGridEndpoint.class);
        assertEquals(CredentialType.AZURE_IDENTITY, endpoint.getConfiguration().getCredentialType());

        assertCredentialOfClient(endpoint, credential);
    }

    @Test
    void explicitTokenCredentialTypeIsKept() throws Exception {
        DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().tenantId("my-tenant").build();
        context.getRegistry().bind("cred", credential);

        EventGridEndpoint endpoint = context.getEndpoint(TOPIC + "?tokenCredential=#cred&credentialType=TOKEN_CREDENTIAL",
                EventGridEndpoint.class);
        assertEquals(CredentialType.TOKEN_CREDENTIAL, endpoint.getConfiguration().getCredentialType());

        assertCredentialOfClient(endpoint, credential);
    }

    @Test
    void azureIdentityWithoutTokenCredentialBuildsOne() throws Exception {
        EventGridEndpoint endpoint = context.getEndpoint(TOPIC + "?credentialType=AZURE_IDENTITY", EventGridEndpoint.class);

        try (MockedConstruction<EventGridPublisherClientBuilder> builders
                = mockConstruction(EventGridPublisherClientBuilder.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            Producer producer = endpoint.createProducer();
            producer.start();
            producer.stop();

            verify(builders.constructed().get(0)).credential(any(DefaultAzureCredential.class));
        }
    }

    private static void assertCredentialOfClient(EventGridEndpoint endpoint, TokenCredential credential) throws Exception {
        try (MockedConstruction<EventGridPublisherClientBuilder> builders
                = mockConstruction(EventGridPublisherClientBuilder.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            Producer producer = endpoint.createProducer();
            producer.start();
            producer.stop();

            assertEquals(1, builders.constructed().size());
            EventGridPublisherClientBuilder builder = builders.constructed().get(0);
            verify(builder).credential(same(credential));
            verify(builder, never()).credential(any(AzureKeyCredential.class));
        }
        assertSame(credential, endpoint.getConfiguration().getTokenCredential());
    }
}
