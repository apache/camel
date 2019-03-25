package org.apache.camel.component.pulsar.utils;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.camel.component.pulsar.PulsarEndpoint;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PulsarUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarUtils.class);

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
            .topic(configuration.getTopic())
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
