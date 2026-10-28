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
package org.apache.camel.component.dapr;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import io.dapr.client.DaprPreviewClient;
import io.dapr.workflows.client.DaprWorkflowClient;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An endpoint closes the Dapr clients that it created when it stops, and never the configured ones, which can be shared
 * with other endpoints.
 */
class DaprEndpointClientOwnershipTest extends CamelTestSupport {

    private static final String URI = "dapr:invokeService?serviceToInvoke=myService&methodToInvoke=myMethod";

    private final DaprClient client = mock(DaprClient.class);
    private final DaprPreviewClient previewClient = mock(DaprPreviewClient.class);
    private final DaprWorkflowClient workflowClient = mock(DaprWorkflowClient.class);

    @Test
    void closesTheClientsItCreated() throws Exception {
        try (MockedConstruction<DaprClientBuilder> builders = mockConstruction(DaprClientBuilder.class, (builder, ctx) -> {
            when(builder.build()).thenReturn(client);
            when(builder.buildPreviewClient()).thenReturn(previewClient);
        });
             MockedConstruction<DaprWorkflowClient> workflowClients = mockConstruction(DaprWorkflowClient.class)) {

            DaprEndpoint endpoint = context.getEndpoint(URI, DaprEndpoint.class);
            assertSame(client, endpoint.getClient());
            assertSame(previewClient, endpoint.getPreviewClient());
            assertEquals(1, workflowClients.constructed().size());

            endpoint.stop();

            verify(client).close();
            verify(previewClient).close();
            verify(workflowClients.constructed().get(0)).close();
        }
    }

    @Test
    void doesNotCloseTheConfiguredClients() throws Exception {
        context.getRegistry().bind("myClient", client);
        context.getRegistry().bind("myPreviewClient", previewClient);
        context.getRegistry().bind("myWorkflowClient", workflowClient);

        DaprEndpoint endpoint = context.getEndpoint(
                URI + "&client=#myClient&previewClient=#myPreviewClient&workflowClient=#myWorkflowClient",
                DaprEndpoint.class);
        assertSame(client, endpoint.getClient());

        endpoint.stop();

        verify(client, never()).close();
        verify(previewClient, never()).close();
        verify(workflowClient, never()).close();

        // and uses them again when it is started again
        endpoint.start();
        assertSame(client, endpoint.getClient());
        assertSame(previewClient, endpoint.getPreviewClient());
        assertSame(workflowClient, endpoint.getWorkflowClient());
    }
}
