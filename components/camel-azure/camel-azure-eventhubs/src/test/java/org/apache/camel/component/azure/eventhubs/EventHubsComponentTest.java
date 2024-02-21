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

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.eventhubs.EventHubConsumerAsyncClient;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventHubsComponentTest extends CamelTestSupport {

    @Test
    public void testCreateEndpointWithNoEventHubsNameOrNameSpace() {
        ResolveEndpointFailedException exception = assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("azure-eventhubs:?sharedAccessKey=string&sharedAccessName=name"));

        assertTrue(
                exception.getMessage()
                        .contains("ConnectionString, ProducerAsyncClient or Namespace and EventHub name must be set"));

        ResolveEndpointFailedException exception2 = assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("azure-eventhubs:name?sharedAccessKey=string&sharedAccessName=name"));

        assertTrue(
                exception2.getMessage()
                        .contains("ConnectionString, ProducerAsyncClient or Namespace and EventHub name must be set"));
    }

    @Test
    public void testCreateEndpointWithNoSuppliedClientsOrKeysOrConnectionStringOrTokenCredential() {
        final String expectedErrorMessage
                = "Azure EventHubs SharedAccessName/SharedAccessKey, ProducerAsyncClient, ConnectionString or TokenCredential must be specified";

        // first case: with no client or key or connectionstring
        assertTrue(getErrorMessage("azure-eventhubs:name/hubName?").contains(expectedErrorMessage));

        // second case: connectionString set
        assertNotNull(context.getEndpoint("azure-eventhubs:?connectionString=string"));

        // third case: either access key or access name set
        assertTrue(getErrorMessage("azure-eventhubs:name/hubName?sharedAccessName=test").contains(expectedErrorMessage));
        assertTrue(getErrorMessage("azure-eventhubs:name/hubName?sharedAccessKey=test").contains(expectedErrorMessage));
        assertNotNull(context.getEndpoint("azure-eventhubs:name/hubName?sharedAccessName=test&sharedAccessKey=test"));

        // forth case: with client set
        final EventHubsConfiguration configuration = new EventHubsConfiguration();
        configuration.setNamespace("test");
        configuration.setConsumerGroupName("testGroup");
        configuration.setSharedAccessKey("dummyKey");
        configuration.setSharedAccessName("dummyUser");

        final EventHubProducerAsyncClient producerAsyncClient
                = EventHubsClientFactory.createEventHubProducerAsyncClient(configuration);

        context.getRegistry().bind("producerClient", producerAsyncClient);

        assertNotNull(context
                .getEndpoint("azure-eventhubs:name/hubName?producerAsyncClient=#producerClient"));
    }

    @Test
    public void testClientAutoDiscovery() {
        final EventHubsConfiguration configuration = new EventHubsConfiguration();
        configuration.setNamespace("test");
        configuration.setConsumerGroupName("testGroup");
        configuration.setSharedAccessKey("dummyKey");
        configuration.setSharedAccessName("dummyUser");

        final EventHubConsumerAsyncClient consumerAsyncClient
                = EventHubsClientFactory.createEventHubConsumerAsyncClient(configuration);
        final EventHubConsumerAsyncClient consumerAsyncClient2
                = EventHubsClientFactory.createEventHubConsumerAsyncClient(configuration);
        final EventHubProducerAsyncClient producerAsyncClient
                = EventHubsClientFactory.createEventHubProducerAsyncClient(configuration);

        // we dont allow more than one instance
        context.getRegistry().bind("consumerClient", consumerAsyncClient);
        context.getRegistry().bind("consumerClient2", consumerAsyncClient2);

        assertThrows(ResolveEndpointFailedException.class, () -> context.getEndpoint("azure-eventhubs:name/hubName"));

        context.getRegistry().bind("producerClient", producerAsyncClient);

        final EventHubsEndpoint endpoint
                = context.getEndpoint("azure-eventhubs:name/hubName?producerAsyncClient=#producerClient",
                        EventHubsEndpoint.class);

        assertSame(producerAsyncClient, endpoint.getConfiguration().getProducerAsyncClient());

        final DefaultAzureCredential tokenCredential = new DefaultAzureCredentialBuilder().build();
        context.getRegistry().bind("tokenCredential", tokenCredential);

        final EventHubsEndpoint endpoint2
                = context.getEndpoint("azure-eventhubs:name/hubName?tokenCredential=#tokenCredential", EventHubsEndpoint.class);

        assertSame(tokenCredential, endpoint2.getConfiguration().getTokenCredential());
    }

    @Test
    public void testCreateEndpointWithConfig() {
        final String uri = "azure-eventhubs:namespace/hubName?sharedAccessName=DummyAccessKeyName"
                           + "&sharedAccessKey=DummyKey"
                           + "&consumerGroupName=testConsumer&prefetchCount=100"
                           + "&checkpointBatchSize=100&checkpointBatchTimeout=1000";

        final EventHubsEndpoint endpoint = context.getEndpoint(uri, EventHubsEndpoint.class);

        assertEquals("namespace", endpoint.getConfiguration().getNamespace());
        assertEquals("hubName", endpoint.getConfiguration().getEventHubName());
        assertEquals("testConsumer", endpoint.getConfiguration().getConsumerGroupName());
        assertEquals("DummyAccessKeyName", endpoint.getConfiguration().getSharedAccessName());
        assertEquals("DummyKey", endpoint.getConfiguration().getSharedAccessKey());
        assertEquals(100, endpoint.getConfiguration().getPrefetchCount());
        assertEquals(100, endpoint.getConfiguration().getCheckpointBatchSize());
        assertEquals(1000, endpoint.getConfiguration().getCheckpointBatchTimeout());
    }

    @Test
    public void testCreateEndpointWithConfigAzureIdentity() {
        final String uri = "azure-eventhubs:namespace/hubName?sharedAccessName=DummyAccessKeyName"
                           + "&sharedAccessKey=DummyKey"
                           + "&consumerGroupName=testConsumer&prefetchCount=100"
                           + "&checkpointBatchSize=100&checkpointBatchTimeout=1000"
                           + "&credentialType=AZURE_IDENTITY";

        final EventHubsEndpoint endpoint = context.getEndpoint(uri, EventHubsEndpoint.class);

        assertEquals("namespace", endpoint.getConfiguration().getNamespace());
        assertEquals("hubName", endpoint.getConfiguration().getEventHubName());
        assertEquals("testConsumer", endpoint.getConfiguration().getConsumerGroupName());
        assertEquals(CredentialType.AZURE_IDENTITY, endpoint.getConfiguration().getCredentialType());
        assertEquals(100, endpoint.getConfiguration().getPrefetchCount());
        assertEquals(100, endpoint.getConfiguration().getCheckpointBatchSize());
        assertEquals(1000, endpoint.getConfiguration().getCheckpointBatchTimeout());
    }

    private String getErrorMessage(final String uri) {
        ResolveEndpointFailedException exception
                = assertThrows(ResolveEndpointFailedException.class, () -> context.getEndpoint(uri));
        return exception.getMessage();
    }

}
