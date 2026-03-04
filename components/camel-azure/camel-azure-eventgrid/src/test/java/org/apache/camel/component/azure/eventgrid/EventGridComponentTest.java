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

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventGridComponentTest extends CamelTestSupport {

    @Test
    public void testCreateEndpointWithNoTopicEndpoint() {
        ResolveEndpointFailedException exception = assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("azure-eventgrid:?accessKey=string"));

        assertTrue(exception.getMessage().contains("Topic endpoint must be specified"));
    }

    @Test
    public void testCreateEndpointWithNoCredentials() {
        final String expectedErrorMessage
                = "Azure EventGrid AccessKey, AzureKeyCredential, TokenCredential or Azure Identity must be specified";

        ResolveEndpointFailedException exception = assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("azure-eventgrid:https://mytopic.eventgrid.azure.net/api/events"));

        assertTrue(exception.getMessage().contains(expectedErrorMessage));
    }

    @Test
    public void testCreateEndpointWithAccessKey() {
        final String uri = "azure-eventgrid:https://mytopic.eventgrid.azure.net/api/events?accessKey=dummyKey";

        final EventGridEndpoint endpoint = context.getEndpoint(uri, EventGridEndpoint.class);

        assertNotNull(endpoint);
        assertEquals("https://mytopic.eventgrid.azure.net/api/events", endpoint.getConfiguration().getTopicEndpoint());
        assertEquals("dummyKey", endpoint.getConfiguration().getAccessKey());
        assertEquals(CredentialType.ACCESS_KEY, endpoint.getConfiguration().getCredentialType());
    }

    @Test
    public void testCreateEndpointWithAzureIdentity() {
        final String uri
                = "azure-eventgrid:https://mytopic.eventgrid.azure.net/api/events?credentialType=AZURE_IDENTITY";

        final EventGridEndpoint endpoint = context.getEndpoint(uri, EventGridEndpoint.class);

        assertNotNull(endpoint);
        assertEquals("https://mytopic.eventgrid.azure.net/api/events", endpoint.getConfiguration().getTopicEndpoint());
        assertEquals(CredentialType.AZURE_IDENTITY, endpoint.getConfiguration().getCredentialType());
    }

    @Test
    public void testCreateEndpointWithTokenCredential() {
        final DefaultAzureCredential tokenCredential = new DefaultAzureCredentialBuilder().build();
        context.getRegistry().bind("tokenCredential", tokenCredential);

        final String uri
                = "azure-eventgrid:https://mytopic.eventgrid.azure.net/api/events?tokenCredential=#tokenCredential";

        final EventGridEndpoint endpoint = context.getEndpoint(uri, EventGridEndpoint.class);

        assertNotNull(endpoint);
        assertSame(tokenCredential, endpoint.getConfiguration().getTokenCredential());
        assertEquals(CredentialType.AZURE_IDENTITY, endpoint.getConfiguration().getCredentialType());
    }

    @Test
    public void testProducerOnlyComponent() {
        final String uri = "azure-eventgrid:https://mytopic.eventgrid.azure.net/api/events?accessKey=dummyKey";

        final EventGridEndpoint endpoint = context.getEndpoint(uri, EventGridEndpoint.class);

        assertThrows(UnsupportedOperationException.class, () -> endpoint.createConsumer(exchange -> {
        }));
    }
}
