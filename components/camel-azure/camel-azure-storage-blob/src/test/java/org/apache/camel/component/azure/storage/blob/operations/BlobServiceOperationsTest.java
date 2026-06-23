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
package org.apache.camel.component.azure.storage.blob.operations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.azure.storage.blob.models.TaggedBlobItem;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.client.BlobServiceClientWrapper;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
class BlobServiceOperationsTest extends CamelTestSupport {

    private BlobConfiguration configuration;

    @Mock
    private BlobServiceClientWrapper client;

    @BeforeEach
    public void setup() {
        configuration = new BlobConfiguration();
        configuration.setAccountName("cameldev");
    }

    @Test
    void testFindBlobsByTagsUsesHeaderFilter() {
        final BlobServiceOperations operations = new BlobServiceOperations(configuration, client);

        final Map<String, String> tags = new HashMap<>();
        tags.put("Environment", "Production");
        final TaggedBlobItem item = new TaggedBlobItem("container-1", "blob-1", tags);
        when(client.findBlobsByTags(any(), any(), any())).thenReturn(List.of(item));

        final Exchange exchange = new DefaultExchange(context);
        final String filter = "\"Environment\" = 'Production'";
        exchange.getIn().setHeader(BlobConstants.BLOB_TAG_FILTER, filter);

        final BlobOperationResponse response = operations.findBlobsByTags(exchange);

        assertNotNull(response);
        @SuppressWarnings("unchecked")
        final List<TaggedBlobItem> body = (List<TaggedBlobItem>) response.getBody();
        assertEquals(1, body.size());
        assertEquals("blob-1", body.get(0).getName());
        verify(client).findBlobsByTags(eq(filter), isNull(), isNull());
    }

    @Test
    void testFindBlobsByTagsFallsBackToBody() {
        final BlobServiceOperations operations = new BlobServiceOperations(configuration, client);

        when(client.findBlobsByTags(any(), any(), any())).thenReturn(List.of());

        final Exchange exchange = new DefaultExchange(context);
        final String filter = "\"Status\" = 'Active'";
        exchange.getIn().setBody(filter);

        operations.findBlobsByTags(exchange);

        verify(client).findBlobsByTags(eq(filter), isNull(), isNull());
    }

    @Test
    void testFindBlobsByTagsThrowsWhenFilterMissing() {
        final BlobServiceOperations operations = new BlobServiceOperations(configuration, client);

        final Exchange exchange = new DefaultExchange(context);

        assertThrows(IllegalArgumentException.class, () -> operations.findBlobsByTags(exchange));
    }
}
