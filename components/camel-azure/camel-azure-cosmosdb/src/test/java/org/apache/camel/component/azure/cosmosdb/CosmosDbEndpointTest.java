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
package org.apache.camel.component.azure.cosmosdb;

import java.util.HashMap;
import java.util.Map;

import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmosDbEndpointTest extends CamelTestSupport {

    @Test
    void testCreateEndpointWithNoClientOrEndpoint() {
        assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("azure-cosmosdb://"));

        assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("azure-cosmosdb://test?databaseEndpoint=https://test.com"));

        assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("azure-cosmosdb://test?accountKey=myKey"));
    }

    @Test
    void testCreateEndpointWithConfig() throws Exception {
        final String uri = "azure-cosmosdb://mydb/myContainer";
        final String remaining = "mydb/myContainer";
        final Map<String, Object> params = new HashMap<>();
        params.put("databaseEndpoint", "https://test.com:443");
        params.put("createDatabaseIfNotExists", "true");
        params.put("accountKey", "myKey");

        final CosmosDbEndpoint endpoint = (CosmosDbEndpoint) context.getComponent("azure-cosmosdb", CosmosDbComponent.class)
                .createEndpoint(uri, remaining, params);

        assertEquals("mydb", endpoint.getConfiguration().getDatabaseName());
        assertEquals("myContainer", endpoint.getConfiguration().getContainerName());
        assertEquals("https://test.com:443", endpoint.getConfiguration().getDatabaseEndpoint());
        assertEquals("myKey", endpoint.getConfiguration().getAccountKey());
        assertEquals(CredentialType.SHARED_ACCOUNT_KEY, endpoint.getConfiguration().getCredentialType());
        assertTrue(endpoint.getConfiguration().isCreateDatabaseIfNotExists());
    }

    @Test
    void testCreateEndpointWithIdentityConfig() throws Exception {
        final String uri = "azure-cosmosdb://mydb/myContainer";
        final String remaining = "mydb/myContainer";
        final Map<String, Object> params = new HashMap<>();
        params.put("databaseEndpoint", "https://test.com:443");
        params.put("createDatabaseIfNotExists", "true");
        params.put("accountKey", "myKey");
        params.put("credentialType", CredentialType.AZURE_IDENTITY);

        final CosmosDbEndpoint endpoint = (CosmosDbEndpoint) context.getComponent("azure-cosmosdb", CosmosDbComponent.class)
                .createEndpoint(uri, remaining, params);

        assertEquals("mydb", endpoint.getConfiguration().getDatabaseName());
        assertEquals("myContainer", endpoint.getConfiguration().getContainerName());
        assertEquals("https://test.com:443", endpoint.getConfiguration().getDatabaseEndpoint());
        assertEquals("myKey", endpoint.getConfiguration().getAccountKey());
        assertEquals(CredentialType.AZURE_IDENTITY, endpoint.getConfiguration().getCredentialType());
        assertTrue(endpoint.getConfiguration().isCreateDatabaseIfNotExists());
    }

    @Test
    void testCreateEndpointWithIndexingPolicyConfig() throws Exception {
        final String uri = "azure-cosmosdb://mydb/myContainer";
        final String remaining = "mydb/myContainer";
        final Map<String, Object> params = new HashMap<>();
        params.put("databaseEndpoint", "https://test.com:443");
        params.put("createDatabaseIfNotExists", "true");
        params.put("accountKey", "myKey");
        params.put("indexingPolicy", new IndexingPolicy().setIndexingMode(IndexingMode.LAZY));

        final CosmosDbEndpoint endpoint = (CosmosDbEndpoint) context.getComponent("azure-cosmosdb", CosmosDbComponent.class)
                .createEndpoint(uri, remaining, params);

        assertEquals("mydb", endpoint.getConfiguration().getDatabaseName());
        assertEquals("myContainer", endpoint.getConfiguration().getContainerName());
        assertEquals("https://test.com:443", endpoint.getConfiguration().getDatabaseEndpoint());
        assertEquals("myKey", endpoint.getConfiguration().getAccountKey());
        assertEquals("Lazy", endpoint.getConfiguration().getIndexingPolicy().getIndexingMode().toString());
        assertEquals(CredentialType.SHARED_ACCOUNT_KEY, endpoint.getConfiguration().getCredentialType());
        assertTrue(endpoint.getConfiguration().isCreateDatabaseIfNotExists());

    }

    @Test
    void testCreateConsumerWithInvalidConfig() throws Exception {
        final String uri = "azure-cosmosdb://mydb/myContainer";
        String remaining = "mydb";
        final Map<String, Object> params = new HashMap<>();
        params.put("databaseEndpoint", "https://test.com:443");
        params.put("createDatabaseIfNotExists", "true");
        params.put("accountKey", "myKey");

        final CosmosDbEndpoint endpoint = (CosmosDbEndpoint) context.getComponent("azure-cosmosdb", CosmosDbComponent.class)
                .createEndpoint(uri, remaining, params);
        assertThrows(IllegalArgumentException.class, () -> endpoint.createConsumer(exchange -> {
        }));

        params.put("databaseEndpoint", "https://test.com:443");
        params.put("createDatabaseIfNotExists", "true");
        params.put("accountKey", "myKey");
        remaining = "/mydb";
        final CosmosDbEndpoint endpoint2 = (CosmosDbEndpoint) context.getComponent("azure-cosmosdb", CosmosDbComponent.class)
                .createEndpoint(uri, remaining, params);
        assertThrows(IllegalArgumentException.class, () -> endpoint2.createConsumer(exchange -> {
        }));

        params.put("databaseEndpoint", "https://test.com:443");
        params.put("createDatabaseIfNotExists", "true");
        params.put("accountKey", "myKey");
        remaining = "mydb/";
        final CosmosDbEndpoint endpoint3 = (CosmosDbEndpoint) context.getComponent("azure-cosmosdb", CosmosDbComponent.class)
                .createEndpoint(uri, remaining, params);
        assertThrows(IllegalArgumentException.class, () -> endpoint3.createConsumer(exchange -> {
        }));

        params.put("databaseEndpoint", "https://test.com:443");
        params.put("createDatabaseIfNotExists", "true");
        params.put("accountKey", "myKey");
        remaining = "";
        final CosmosDbEndpoint endpoint4 = (CosmosDbEndpoint) context.getComponent("azure-cosmosdb", CosmosDbComponent.class)
                .createEndpoint(uri, remaining, params);
        assertThrows(IllegalArgumentException.class, () -> endpoint4.createConsumer(exchange -> {
        }));

        params.put("databaseEndpoint", "https://test.com:443");
        params.put("createDatabaseIfNotExists", "true");
        params.put("accountKey", "myKey");
        remaining = "mydb/mycn";
        final CosmosDbEndpoint endpoint5 = (CosmosDbEndpoint) context.getComponent("azure-cosmosdb", CosmosDbComponent.class)
                .createEndpoint(uri, remaining, params);
        assertNotNull(endpoint5.createConsumer(exchange -> {
        }));
    }

}
