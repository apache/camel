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
package org.apache.camel.component.salesforce.internal.client;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatch;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatchResponse;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

public class DefaultCompositeApiClientTest {

    private static final String V34_0 = "34.0";

    private static final String V35_0 = "35.0";

    @Test
    public void shouldAllowNewerPayloadsWhenConfiguredWithNewerVersion() throws SalesforceException {
        final SObjectBatch batch = new SObjectBatch(V34_0);

        DefaultCompositeApiClient.checkCompositeBatchVersion(V35_0, batch.getVersion());
    }

    @Test
    public void shouldNotAllowNewerPayloadsWhenConfiguredWithOlderVersion() throws SalesforceException {
        final SObjectBatch batch = new SObjectBatch(V35_0);

        assertThrows(SalesforceException.class,
                () -> DefaultCompositeApiClient.checkCompositeBatchVersion(V34_0, batch.getVersion()));
    }

    @Test
    public void shouldUseCompositeApiFromVersion34Onwards() throws SalesforceException {
        final SObjectBatch batch = new SObjectBatch(V34_0);

        DefaultCompositeApiClient.checkCompositeBatchVersion(V34_0, batch.getVersion());
    }

    @Test
    public void shouldNotFailOnUnreadableResponse() throws SalesforceException {
        final DefaultCompositeApiClient client = new DefaultCompositeApiClient(
                new SalesforceEndpointConfig(), V35_0, mock(SalesforceSession.class), mock(SalesforceHttpClient.class),
                new SalesforceLoginConfig());

        assertEquals(Optional.empty(), client.tryToReadResponse(SObjectBatchResponse.class,
                new ByteArrayInputStream("not json".getBytes(StandardCharsets.UTF_8))));
    }
}
