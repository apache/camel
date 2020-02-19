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
package org.apache.camel.component.pulsar.utils;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.PulsarClientException;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class PulsarUtilsTest {

    @Test
    public void givenConsumerQueueIsEmptywhenIStopConsumersverifyEmptyQueueIsReturned() throws PulsarClientException {
        Queue<Consumer<byte[]>> expected = PulsarUtils.stopConsumers(new ConcurrentLinkedQueue<Consumer<byte[]>>());

        assertTrue(expected.isEmpty());
    }

    @Test
    public void givenConsumerQueueIsNotEmptywhenIStopConsumersverifyEmptyQueueIsReturned() throws PulsarClientException {
        Queue<Consumer<byte[]>> consumers = new ConcurrentLinkedQueue<>();
        consumers.add(mock(Consumer.class));

        Queue<Consumer<byte[]>> expected = PulsarUtils.stopConsumers(consumers);

        assertTrue(expected.isEmpty());
    }

    @Test
    public void givenConsumerQueueIsNotEmptywhenIStopConsumersverifyCallToCloseAndUnsubscribeConsumer() throws PulsarClientException {
        Consumer<byte[]> consumer = mock(Consumer.class);

        Queue<Consumer<byte[]>> consumers = new ConcurrentLinkedQueue<>();
        consumers.add(consumer);

        PulsarUtils.stopConsumers(consumers);

        verify(consumer).close();
    }

    @Test(expected = PulsarClientException.class)
    public void givenConsumerThrowsPulsarClientExceptionwhenIStopConsumersverifyExceptionIsThrown() throws PulsarClientException {
        Consumer<byte[]> consumer = mock(Consumer.class);

        doThrow(new PulsarClientException("A Pulsar Client exception occurred")).when(consumer).close();

        consumer.close();

        verify(consumer).close();
    }
}
