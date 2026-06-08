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
package org.apache.camel.component.google.pubsub;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.spi.ExchangeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GooglePubsubConsumerShutdownTest {

    private final GooglePubsubEndpoint endpoint = mock();
    private final Processor processor = mock();
    private final CamelContext context = mock();
    private final ExtendedCamelContext ecc = mock();
    private final ExchangeFactory ef = mock();

    @BeforeEach
    void setUp() {
        when(endpoint.getCamelContext()).thenReturn(context);
        when(context.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(ef);
        when(ef.newExchangeFactory(any())).thenReturn(ef);
    }

    @Test
    void deferShutdownReturnsTrue() {
        GooglePubsubConsumer consumer = new GooglePubsubConsumer(endpoint, processor);

        boolean deferred = consumer.deferShutdown(ShutdownRunningTask.CompleteAllTasks);

        assertTrue(deferred);
    }

    @Test
    void pendingExchangesSizeIsZeroInitially() {
        GooglePubsubConsumer consumer = new GooglePubsubConsumer(endpoint, processor);

        assertEquals(0, consumer.getPendingExchangesSize());
    }

    @Test
    void pendingExchangesSizeTracksInflightMessages() {
        GooglePubsubConsumer consumer = new GooglePubsubConsumer(endpoint, processor);

        assertEquals(0, consumer.getPendingExchangesSize());

        consumer.incrementPendingExchanges();
        assertEquals(1, consumer.getPendingExchangesSize());

        consumer.incrementPendingExchanges();
        assertEquals(2, consumer.getPendingExchangesSize());

        consumer.decrementPendingExchanges();
        assertEquals(1, consumer.getPendingExchangesSize());

        consumer.decrementPendingExchanges();
        assertEquals(0, consumer.getPendingExchangesSize());
    }
}
