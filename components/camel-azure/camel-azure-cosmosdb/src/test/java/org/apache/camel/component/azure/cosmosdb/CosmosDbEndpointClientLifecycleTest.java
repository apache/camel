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

import com.azure.cosmos.CosmosAsyncClient;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.cosmosdb.client.CosmosDbClientFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A CosmosAsyncClient set on the component or autowired from the registry is shared by all the azure-cosmosdb endpoints
 * (and often by the application): an endpoint that stops must not close it. A client the endpoint created itself is
 * closed when it stops.
 */
class CosmosDbEndpointClientLifecycleTest extends CamelTestSupport {

    @BindToRegistry("cosmosClient")
    private final CosmosAsyncClient sharedClient = mock(CosmosAsyncClient.class, "shared");

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders").to("azure-cosmosdb:myDb/orders");
                from("direct:audit").routeId("audit").to("azure-cosmosdb:myDb/audit");
            }
        };
    }

    @Test
    void removingARouteDoesNotCloseTheSharedClient() throws Exception {
        CosmosDbEndpoint orders = context.getEndpoint("azure-cosmosdb:myDb/orders", CosmosDbEndpoint.class);
        CosmosDbEndpoint audit = context.getEndpoint("azure-cosmosdb:myDb/audit", CosmosDbEndpoint.class);
        assertSame(sharedClient, orders.getCosmosAsyncClient());
        assertSame(sharedClient, audit.getCosmosAsyncClient());

        // removing the route stops the endpoints that only this route uses
        context.getRouteController().stopRoute("orders");
        assertTrue(context.removeRoute("orders"));
        assertFalse(orders.isStarted());

        // the other endpoint (and the application) still use the client
        verify(sharedClient, never()).close();
        assertSame(sharedClient, audit.getCosmosAsyncClient());
    }

    @Test
    void anEndpointClosesTheClientItCreated() throws Exception {
        CosmosDbConfiguration configuration = new CosmosDbConfiguration();
        configuration.setDatabaseName("myDb");
        configuration.setContainerName("own");
        configuration.setDatabaseEndpoint("https://test.com:443");
        configuration.setAccountKey("myKey");
        CosmosDbComponent component = context.getComponent("azure-cosmosdb", CosmosDbComponent.class);

        CosmosAsyncClient firstClient = mock(CosmosAsyncClient.class, "first");
        CosmosAsyncClient secondClient = mock(CosmosAsyncClient.class, "second");
        try (MockedStatic<CosmosDbClientFactory> factory = mockStatic(CosmosDbClientFactory.class)) {
            factory.when(() -> CosmosDbClientFactory.createCosmosAsyncClient(any()))
                    .thenReturn(firstClient, secondClient);

            CosmosDbEndpoint endpoint = new CosmosDbEndpoint("azure-cosmosdb:myDb/own", component, configuration);
            // not given the client of the registry
            endpoint.setAutowiredEnabled(false);
            endpoint.start();
            assertSame(firstClient, endpoint.getCosmosAsyncClient());
            endpoint.stop();
            verify(firstClient).close();

            // a start after a stop creates a new client
            endpoint.start();
            assertSame(secondClient, endpoint.getCosmosAsyncClient());
            endpoint.stop();
            verify(secondClient).close();
        }
        verify(sharedClient, never()).close();
    }
}
