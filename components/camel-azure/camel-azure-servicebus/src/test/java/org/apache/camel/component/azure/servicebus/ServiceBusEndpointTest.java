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
package org.apache.camel.component.azure.servicebus;

import java.util.HashMap;
import java.util.Map;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceBusEndpointTest extends CamelTestSupport {

    @Test
    void testCreateWithInvalidData() {
        assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("azure-servicebus:test//?"));

        assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("azure-servicebus://?connectionString=test"));

        // provided credential but no fully qualified namespace
        assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("azure-servicebus:test?tokenCredential=credential"));
    }

    @Test
    void testCreateEndpointWithConfig() throws Exception {
        final String uri = "azure-servicebus://testTopicOrQueue";
        final String remaining = "testTopicOrQueue";
        final Map<String, Object> params = new HashMap<>();
        params.put("serviceBusType", ServiceBusType.topic);
        params.put("prefetchCount", 10);
        params.put("connectionString", "testString");
        params.put("binary", "true");

        final ServiceBusEndpoint endpoint
                = (ServiceBusEndpoint) context.getComponent("azure-servicebus", ServiceBusComponent.class)
                        .createEndpoint(uri, remaining, params);

        assertEquals(ServiceBusType.topic, endpoint.getConfiguration().getServiceBusType());
        assertEquals("testTopicOrQueue", endpoint.getConfiguration().getTopicOrQueueName());
        assertEquals(10, endpoint.getConfiguration().getPrefetchCount());
        assertEquals("testString", endpoint.getConfiguration().getConnectionString());
        assertEquals(true, endpoint.getConfiguration().isBinary());
    }

    @Test
    void testCreateEndpointWithFqns() throws Exception {
        final String uri = "azure-servicebus://testTopicOrQueue";
        final String remaining = "testTopicOrQueue";
        final String fullyQualifiedNamespace = "namespace.servicebus.windows.net";
        final Map<String, Object> params = new HashMap<>();
        params.put("serviceBusType", ServiceBusType.topic);
        params.put("prefetchCount", 10);
        params.put("fullyQualifiedNamespace", fullyQualifiedNamespace);

        final ServiceBusEndpoint endpoint
                = (ServiceBusEndpoint) context.getComponent("azure-servicebus", ServiceBusComponent.class)
                        .createEndpoint(uri, remaining, params);

        assertEquals(ServiceBusType.topic, endpoint.getConfiguration().getServiceBusType());
        assertEquals("testTopicOrQueue", endpoint.getConfiguration().getTopicOrQueueName());
        assertEquals(10, endpoint.getConfiguration().getPrefetchCount());
        assertEquals(fullyQualifiedNamespace, endpoint.getConfiguration().getFullyQualifiedNamespace());
        assertNull(endpoint.getConfiguration().getTokenCredential());
    }

    @Test
    void testCreateEndpointWithFqnsAndCredential() throws Exception {
        final String uri = "azure-servicebus://testTopicOrQueue";
        final String remaining = "testTopicOrQueue";
        final String fullyQualifiedNamespace = "namespace.servicebus.windows.net";
        final TokenCredential credential = new DefaultAzureCredentialBuilder().build();
        final Map<String, Object> params = new HashMap<>();
        params.put("serviceBusType", ServiceBusType.topic);
        params.put("prefetchCount", 10);
        params.put("fullyQualifiedNamespace", fullyQualifiedNamespace);
        params.put("tokenCredential", credential);

        final ServiceBusEndpoint endpoint
                = (ServiceBusEndpoint) context.getComponent("azure-servicebus", ServiceBusComponent.class)
                        .createEndpoint(uri, remaining, params);

        assertEquals(ServiceBusType.topic, endpoint.getConfiguration().getServiceBusType());
        assertEquals("testTopicOrQueue", endpoint.getConfiguration().getTopicOrQueueName());
        assertEquals(10, endpoint.getConfiguration().getPrefetchCount());
        assertEquals(fullyQualifiedNamespace, endpoint.getConfiguration().getFullyQualifiedNamespace());
        assertEquals(credential, endpoint.getConfiguration().getTokenCredential());
    }

    @Test
    void testCreateEndpointWithFqnsAndCredentialFromRegistry() throws Exception {
        final String uri = "azure-servicebus://testTopicOrQueue";
        final String remaining = "testTopicOrQueue";
        final String fullyQualifiedNamespace = "namespace.servicebus.windows.net";
        final TokenCredential credential = new DefaultAzureCredentialBuilder().build();
        final Map<String, Object> params = new HashMap<>();
        context().getRegistry().bind("tokenCredential", credential);
        params.put("serviceBusType", ServiceBusType.topic);
        params.put("prefetchCount", 10);
        params.put("fullyQualifiedNamespace", fullyQualifiedNamespace);

        final ServiceBusEndpoint endpoint
                = (ServiceBusEndpoint) context.getComponent("azure-servicebus", ServiceBusComponent.class)
                        .createEndpoint(uri, remaining, params);

        assertEquals(ServiceBusType.topic, endpoint.getConfiguration().getServiceBusType());
        assertEquals("testTopicOrQueue", endpoint.getConfiguration().getTopicOrQueueName());
        assertEquals(10, endpoint.getConfiguration().getPrefetchCount());
        assertEquals(fullyQualifiedNamespace, endpoint.getConfiguration().getFullyQualifiedNamespace());
        assertEquals(credential, endpoint.getConfiguration().getTokenCredential());
    }

    @Test
    void testCreateEndpointWithAzureIdentity() throws Exception {
        final String uri = "azure-servicebus://testTopicOrQueue";
        final String remaining = "testTopicOrQueue";
        final String fullyQualifiedNamespace = "namespace.servicebus.windows.net";
        final TokenCredential credential = new DefaultAzureCredentialBuilder().build();
        final Map<String, Object> params = new HashMap<>();
        params.put("serviceBusType", ServiceBusType.topic);
        params.put("prefetchCount", 10);
        params.put("fullyQualifiedNamespace", fullyQualifiedNamespace);
        params.put("credentialType", CredentialType.AZURE_IDENTITY);

        final ServiceBusEndpoint endpoint
                = (ServiceBusEndpoint) context.getComponent("azure-servicebus", ServiceBusComponent.class)
                        .createEndpoint(uri, remaining, params);

        assertEquals(ServiceBusType.topic, endpoint.getConfiguration().getServiceBusType());
        assertEquals("testTopicOrQueue", endpoint.getConfiguration().getTopicOrQueueName());
        assertEquals(10, endpoint.getConfiguration().getPrefetchCount());
        assertEquals(fullyQualifiedNamespace, endpoint.getConfiguration().getFullyQualifiedNamespace());
        assertNull(endpoint.getConfiguration().getTokenCredential());
    }
}
