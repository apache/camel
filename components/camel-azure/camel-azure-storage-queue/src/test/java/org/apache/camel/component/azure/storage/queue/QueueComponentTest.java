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
package org.apache.camel.component.azure.storage.queue;

import java.time.Duration;

import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.queue.QueueServiceClient;
import org.apache.camel.Producer;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.azure.storage.queue.client.QueueClientFactory;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueueComponentTest extends CamelTestSupport {

    @Test
    public void testCreateEndpointWithMinConfigForClientOnly() {
        // fail because we never pass any key or client
        assertThrows(ResolveEndpointFailedException.class, () -> context.getEndpoint("azure-storage-queue://sss"));

        final QueueConfiguration configuration = new QueueConfiguration();
        configuration.setCredentials(new StorageSharedKeyCredential("fake", "fake"));
        final QueueServiceClient client = QueueClientFactory.createQueueServiceClient(configuration);

        context.getRegistry().bind("client", client);

        final QueueEndpoint endpoint
                = (QueueEndpoint) context.getEndpoint("azure-storage-queue://camelazure/testqueue?serviceClient=#client");

        doTestCreateEndpointWithMinConfig(endpoint, true);
    }

    @Test
    public void testCreateEndpointWithMinConfigForCredsOnly() {
        context.getRegistry().bind("creds", new StorageSharedKeyCredential("fake", "fake"));

        final QueueEndpoint endpoint
                = (QueueEndpoint) context.getEndpoint("azure-storage-queue://camelazure/testqueue?credentials=#creds");

        doTestCreateEndpointWithMinConfig(endpoint, false);
    }

    @Test
    public void testCreateEndpointWithMinConfigForAzIdentity() {

        final QueueEndpoint endpoint
                = (QueueEndpoint) context
                        .getEndpoint("azure-storage-queue://camelazure/testqueue?credentialType=AZURE_IDENTITY");

        doTestCreateEndpointWithMinConfigForAzIdentity(endpoint);
    }

    private void doTestCreateEndpointWithMinConfig(final QueueEndpoint endpoint, boolean clientExpected) {
        assertEquals("camelazure", endpoint.getConfiguration().getAccountName());
        assertEquals("testqueue", endpoint.getConfiguration().getQueueName());
        if (clientExpected) {
            assertNotNull(endpoint.getConfiguration().getServiceClient());
            assertNull(endpoint.getConfiguration().getCredentials());
        } else {
            assertNull(endpoint.getConfiguration().getServiceClient());
            assertNotNull(endpoint.getConfiguration().getCredentials());
        }
        assertEquals(QueueOperationDefinition.sendMessage, endpoint.getConfiguration().getOperation());

        assertNull(endpoint.getConfiguration().getVisibilityTimeout());
        assertNull(endpoint.getConfiguration().getTimeToLive());
        assertEquals(1, endpoint.getConfiguration().getMaxMessages());
    }

    private void doTestCreateEndpointWithMinConfigForAzIdentity(final QueueEndpoint endpoint) {
        assertEquals("camelazure", endpoint.getConfiguration().getAccountName());
        assertEquals("testqueue", endpoint.getConfiguration().getQueueName());
        assertNull(endpoint.getConfiguration().getServiceClient());
        assertNull(endpoint.getConfiguration().getCredentials());
        assertEquals(QueueOperationDefinition.sendMessage, endpoint.getConfiguration().getOperation());

        assertNull(endpoint.getConfiguration().getVisibilityTimeout());
        assertNull(endpoint.getConfiguration().getTimeToLive());
        assertEquals(1, endpoint.getConfiguration().getMaxMessages());
        assertEquals(CredentialType.AZURE_IDENTITY, endpoint.getConfiguration().getCredentialType());
    }

    @Test
    public void testCreateEndpointWithMaxConfig() {
        context.getRegistry().bind("creds", new StorageSharedKeyCredential("fake", "fake"));

        final String uri = "azure-storage-queue://camelazure/testqueue"
                           + "?credentials=#creds&operation=deleteQueue&timeToLive=PT100s&visibilityTimeout=PT10s&maxMessages=1";

        final QueueEndpoint endpoint = (QueueEndpoint) context.getEndpoint(uri);

        assertEquals("camelazure", endpoint.getConfiguration().getAccountName());
        assertEquals("testqueue", endpoint.getConfiguration().getQueueName());
        assertNull(endpoint.getConfiguration().getServiceClient());
        assertEquals(QueueOperationDefinition.deleteQueue, endpoint.getConfiguration().getOperation());
        assertEquals(Duration.ofSeconds(100), endpoint.getConfiguration().getTimeToLive());
        assertEquals(Duration.ofSeconds(10), endpoint.getConfiguration().getVisibilityTimeout());
        assertEquals(1, endpoint.getConfiguration().getMaxMessages());
    }

    @Test
    public void testNoQueueNameProducerWithOpNeedsQueueName() throws Exception {
        context.getRegistry().bind("creds", new StorageSharedKeyCredential("fake", "fake"));

        final QueueEndpoint endpoint = (QueueEndpoint) context
                .getEndpoint("azure-storage-queue://camelazure?credentials=#creds&operation=deleteQueue");

        Producer producer = endpoint.createProducer();
        DefaultExchange exchange = new DefaultExchange(context);
        assertThrows(IllegalArgumentException.class, () -> producer.process(exchange));
    }

    @Test
    public void testNoQueueNameConsumer() {
        context.getRegistry().bind("creds", new StorageSharedKeyCredential("fake", "fake"));

        final QueueEndpoint endpoint = (QueueEndpoint) context
                .getEndpoint("azure-storage-queue://camelazure?credentials=#creds&operation=deleteQueue");

        assertThrows(IllegalArgumentException.class, () -> endpoint.createConsumer(null));
    }
}
