package org.apache.camel.component.pulsar.utils;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.camel.component.pulsar.PulsarEndpoint;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClientException;

public final class PulsarUtils {

    public static Queue<Consumer<byte[]>> stopConsumers(final Queue<Consumer<byte[]>> consumers) throws PulsarClientException {
        while (!consumers.isEmpty()) {
            Consumer<byte[]> consumer = consumers.poll();

            consumer.unsubscribe();
            consumer.close();
        }

        return new ConcurrentLinkedQueue<>();
    }

    public static Producer<byte[]> createProducer(final PulsarEndpoint pulsarEndpoint) throws PulsarClientException {
        final PulsarEndpointConfiguration configuration = pulsarEndpoint.getConfiguration();

        return pulsarEndpoint.getPulsarClient()
            .newProducer()
            .topic(pulsarEndpoint.getTopic())
            .producerName(configuration.getProducerName())
            .create();
    }

    public static Queue<Producer<byte[]>> stopProducer(final Queue<Producer<byte[]>> producers) throws PulsarClientException {
        while (!producers.isEmpty()) {
            Producer<byte[]> producer = producers.poll();
            producer.close();
        }

        return new LinkedList<>();
    }
}
