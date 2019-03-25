package org.apache.camel.component.pulsar.utils;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.camel.component.pulsar.PulsarComponent;
import org.apache.camel.component.pulsar.PulsarEndpoint;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.camel.component.pulsar.utils.consumers.ConsumerCreationStrategyFactory;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.junit.Test;

public class PulsarUtilsTest {

    @Test
    public void givenConsumerQueueIsEmpty_whenIStopConsumers_verifyEmptyQueueIsReturned() throws PulsarClientException {
        Queue<Consumer<byte[]>> expected = PulsarUtils.stopConsumers(new ConcurrentLinkedQueue<Consumer<byte[]>>());

        assertTrue(expected.isEmpty());
    }

    @Test
    public void givenConsumerQueueIsNotEmpty_whenIStopConsumers_verifyEmptyQueueIsReturned() throws PulsarClientException {
        Queue<Consumer<byte[]>> consumers = new ConcurrentLinkedQueue<>();
        consumers.add(mock(Consumer.class));

        Queue<Consumer<byte[]>> expected = PulsarUtils.stopConsumers(consumers);

        assertTrue(expected.isEmpty());
    }

    @Test
    public void givenConsumerQueueIsNotEmpty_whenIStopConsumers_verifyCallToCloseAndUnsubscribeConsumer() throws PulsarClientException {
        Consumer<byte[]> consumer = mock(Consumer.class);

        Queue<Consumer<byte[]>> consumers = new ConcurrentLinkedQueue<>();
        consumers.add(consumer);

        PulsarUtils.stopConsumers(consumers);

        verify(consumer).unsubscribe();
        verify(consumer).close();
    }

    @Test(expected = PulsarClientException.class)
    public void givenConsumerThrowsPulsarClientException_whenIStopConsumers_verifyExceptionIsThrown() throws PulsarClientException {
        Consumer<byte[]> consumer = mock(Consumer.class);

        Queue<Consumer<byte[]>> consumers = new ConcurrentLinkedQueue<>();
        consumers.add(consumer);

        doThrow(new PulsarClientException("A Pulsar Client exception occurred")).when(consumer).close();

        PulsarUtils.stopConsumers(consumers);
    }
}