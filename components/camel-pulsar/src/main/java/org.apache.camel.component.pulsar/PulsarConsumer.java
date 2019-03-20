package org.apache.camel.component.pulsar;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.camel.Processor;
import org.apache.camel.component.pulsar.utils.consumers.ConsumerCreationStrategy;
import org.apache.camel.component.pulsar.utils.consumers.ConsumerCreationStrategyFactory;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarConsumer extends DefaultConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarConsumer.class);

    private final PulsarEndpoint pulsarEndpoint;
    private final ConcurrentLinkedQueue<Consumer<byte[]>> pulsarConsumers;
    private final ConsumerCreationStrategyFactory consumerCreationStrategyFactory;

    private PulsarConsumer(PulsarEndpoint pulsarEndpoint, Processor processor) {
        super(pulsarEndpoint, processor);
        this.pulsarEndpoint = pulsarEndpoint;
        this.pulsarConsumers = new ConcurrentLinkedQueue<>();
        this.consumerCreationStrategyFactory = new ConsumerCreationStrategyFactory(this);
    }

    public static PulsarConsumer create(PulsarEndpoint pulsarEndpoint, Processor processor) {
        return new PulsarConsumer(pulsarEndpoint, processor);
    }

    @Override
    protected void doStart() throws PulsarClientException {
        stopConsumers(pulsarConsumers);

        pulsarConsumers.addAll(createConsumers(pulsarEndpoint));
    }

    @Override
    protected void doStop() throws PulsarClientException {
        stopConsumers(pulsarConsumers);
    }

    @Override
    protected void doSuspend() throws PulsarClientException {
        stopConsumers(pulsarConsumers);
    }

    @Override
    protected void doResume() throws PulsarClientException {
        stopConsumers(pulsarConsumers);
        createConsumers(pulsarEndpoint);
    }

    public void stopConsumers(final Queue<Consumer<byte[]>> consumers) throws PulsarClientException {
        while (!consumers.isEmpty()) {
            Consumer<byte[]> consumer = pulsarConsumers.poll();

            consumer.unsubscribe();
            consumer.close();
        }
    }

    public Collection<Consumer<byte[]>> createConsumers(final PulsarEndpoint pulsarEndpoint) {
        ConsumerCreationStrategy strategy = consumerCreationStrategyFactory.getStrategy(pulsarEndpoint.getConfiguration().getSubscriptionType());

        return strategy.create(pulsarEndpoint);
    }
}