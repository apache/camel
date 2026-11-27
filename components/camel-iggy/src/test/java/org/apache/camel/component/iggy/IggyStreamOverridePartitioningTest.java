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
package org.apache.camel.component.iggy;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.iggy.client.blocking.IggyBaseClient;
import org.apache.iggy.identifier.StreamId;
import org.apache.iggy.message.Partitioning;
import org.apache.iggy.message.PartitioningKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The stream of a CamelIggyStreamOverride header is created when streamId is set, and the partitioning option can be
 * given as text. The Iggy client is a mock, no Iggy server is needed.
 */
public class IggyStreamOverridePartitioningTest extends CamelTestSupport {

    private static final String URI = "iggy:topic?streamName=stream&streamId=1&autoCreateTopic=false";

    @Test
    void theStreamOfTheOverrideHeaderIsCreatedWhenStreamIdIsSet() throws Exception {
        IggyEndpoint endpoint = context.getEndpoint(URI, IggyEndpoint.class);
        IggyBaseClient client = missingStreams();

        // the producer initializes the topic of a message with the CamelIggyStreamOverride header so
        endpoint.initializeTopic(client, "topic", "other");

        verify(client.streams()).getStream(argThat((StreamId id) -> "other".equals(id.getName())));
        verify(client.streams()).createStream("other");
        verify(client.streams(), never()).createStream("stream");
    }

    @Test
    void theStreamOfTheEndpointIsLookedUpByStreamId() throws Exception {
        IggyEndpoint endpoint = context.getEndpoint(URI, IggyEndpoint.class);
        IggyBaseClient client = missingStreams();

        endpoint.initializeTopic(client);

        verify(client.streams()).getStream(argThat((StreamId id) -> Long.valueOf(1).equals(id.getId())));
        verify(client.streams()).createStream("stream");
    }

    @Test
    void thePartitioningOptionAsText() {
        assertPartitioning("balanced", PartitioningKind.Balanced, Partitioning.balanced().value());
        assertPartitioning("partitionId:3", PartitioningKind.PartitionId, Partitioning.partitionId(3L).value());
        assertPartitioning("messagesKey:order-1", PartitioningKind.MessagesKey,
                "order-1".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void anUnknownPartitioningFailsTheEndpoint() {
        assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("iggy:topic?streamName=stream&partitioning=roundRobin"));
    }

    private void assertPartitioning(String text, PartitioningKind kind, byte[] value) {
        Partitioning partitioning = context.getEndpoint("iggy:topic?streamName=stream&partitioning=" + text,
                IggyEndpoint.class).getConfiguration().getPartitioning();

        assertEquals(kind, partitioning.kind(), text);
        assertArrayEquals(value, partitioning.value(), text);
    }

    private static IggyBaseClient missingStreams() {
        IggyBaseClient client = mock(IggyBaseClient.class, RETURNS_DEEP_STUBS);
        when(client.streams().getStream(any(StreamId.class))).thenReturn(Optional.empty());
        return client;
    }
}
