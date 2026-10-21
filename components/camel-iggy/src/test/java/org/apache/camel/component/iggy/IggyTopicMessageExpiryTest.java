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

import java.math.BigInteger;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.iggy.client.blocking.IggyBaseClient;
import org.apache.iggy.identifier.StreamId;
import org.apache.iggy.identifier.TopicId;
import org.apache.iggy.topic.CompressionAlgorithm;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The messageExpiry option is documented in seconds, and Iggy reads the message expiry of a topic in microseconds. The
 * Iggy client is a mock, no Iggy server is needed.
 */
public class IggyTopicMessageExpiryTest {

    private static final BigInteger U64_MAX = new BigInteger("18446744073709551615");

    @Test
    void testOneHourIsSentAsMicroseconds() throws Exception {
        assertEquals(BigInteger.valueOf(3_600_000_000L), createdTopicExpiry("messageExpiry=3600"));
    }

    @Test
    void testOneSecondIsSentAsMicroseconds() throws Exception {
        assertEquals(BigInteger.valueOf(1_000_000L), createdTopicExpiry("messageExpiry=1"));
    }

    @Test
    void testNoExpiryStaysZero() throws Exception {
        assertEquals(BigInteger.ZERO, createdTopicExpiry("messageExpiry=0"));
    }

    @Test
    void testTooLargeExpiryNeverExpires() throws Exception {
        assertEquals(U64_MAX, createdTopicExpiry("messageExpiry=" + Long.MAX_VALUE));
    }

    private static BigInteger createdTopicExpiry(String option) throws Exception {
        IggyBaseClient client = mock(IggyBaseClient.class, RETURNS_DEEP_STUBS);
        when(client.topics().getTopic(any(StreamId.class), any(TopicId.class))).thenReturn(Optional.empty());

        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            IggyEndpoint endpoint = context.getEndpoint(
                    "iggy:topic?streamName=stream&autoCreateStream=false&" + option, IggyEndpoint.class);
            endpoint.initializeTopic(client);
        }

        ArgumentCaptor<BigInteger> expiry = ArgumentCaptor.forClass(BigInteger.class);
        verify(client.topics()).createTopic(any(StreamId.class), anyLong(), any(CompressionAlgorithm.class),
                expiry.capture(), any(BigInteger.class), anyString(), anyMap());
        return expiry.getValue();
    }
}
