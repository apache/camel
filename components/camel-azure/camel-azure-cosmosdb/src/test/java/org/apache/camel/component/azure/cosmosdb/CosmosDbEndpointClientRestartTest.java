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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.cosmosdb.client.CosmosDbClientFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A client that the endpoint created is closed when the CamelContext stops, and the endpoint creates a new one when it
 * is started again. The producer of the started route then uses the new client, not the closed one.
 */
class CosmosDbEndpointClientRestartTest extends CamelTestSupport {

    private static final String URI
            = "azure-cosmosdb:myDb/own?databaseEndpoint=https://test.com:443&accountKey=myKey&operation=createDatabase";

    @Test
    void aProducerUsesTheNewClientAfterTheCamelContextIsStartedAgain() throws Exception {
        CosmosAsyncClient firstClient = mock(CosmosAsyncClient.class, "first");
        CosmosAsyncClient secondClient = mock(CosmosAsyncClient.class, "second");
        when(secondClient.createDatabaseIfNotExists("myDb", null)).thenReturn(Mono.empty());
        try (MockedStatic<CosmosDbClientFactory> factory = mockStatic(CosmosDbClientFactory.class)) {
            factory.when(() -> CosmosDbClientFactory.createCosmosAsyncClient(any()))
                    .thenReturn(firstClient, secondClient);
            // added here, so the endpoint creates its client while the factory is mocked (on this thread)
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:own").routeId("own").to(URI);
                }
            });
            assertSame(firstClient, context.getEndpoint(URI, CosmosDbEndpoint.class).getCosmosAsyncClient());

            context.stop();
            verify(firstClient).close();
            context.start();
        }
        assertSame(secondClient, context.getEndpoint(URI, CosmosDbEndpoint.class).getCosmosAsyncClient());

        Exchange exchange = template.send("direct:own", e -> {
        });

        assertNull(exchange.getException());
        verify(secondClient).createDatabaseIfNotExists("myDb", null);
        verify(firstClient, never()).createDatabaseIfNotExists(any(String.class), any());
    }
}
