package org.apache.camel.component.pulsar;

import static org.apache.camel.component.pulsar.utils.PulsarUtils.stopConsumers;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.camel.Processor;
import org.apache.camel.component.pulsar.utils.consumers.ConsumerCreationStrategy;
import org.apache.camel.component.pulsar.utils.consumers.ConsumerCreationStrategyFactory;
import org.apache.camel.component.pulsar.utils.retry.ExponentialRetryPolicy;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.PulsarClientException;

public class PulsarConsumer extends DefaultConsumer {

    private final PulsarEndpoint pulsarEndpoint;
    private final ConsumerCreationStrategyFactory consumerCreationStrategyFactory;

    private Queue<Consumer<byte[]>> pulsarConsumers;

    private PulsarConsumer(PulsarEndpoint pulsarEndpoint, Processor processor) {
        super(pulsarEndpoint, processor);
        this.pulsarEndpoint = pulsarEndpoint;
        this.pulsarConsumers = new ConcurrentLinkedQueue<>();
        this.consumerCreationStrategyFactory = ConsumerCreationStrategyFactory.create(this, new ExponentialRetryPolicy());
    }

    public static PulsarConsumer create(final PulsarEndpoint pulsarEndpoint, final Processor processor) {
        return new PulsarConsumer(pulsarEndpoint, processor);
    }

    @Override
    protected void doStart() throws PulsarClientException {
        pulsarConsumers = stopConsumers(pulsarConsumers);

        pulsarConsumers.addAll(createConsumers(pulsarEndpoint, consumerCreationStrategyFactory));
    }

    @Override
    protected void doStop() throws PulsarClientException {
        pulsarConsumers = stopConsumers(pulsarConsumers);
    }

    @Override
    protected void doSuspend() throws PulsarClientException {
        pulsarConsumers = stopConsumers(pulsarConsumers);
    }

    @Override
    protected void doResume() throws PulsarClientException {
        pulsarConsumers = stopConsumers(pulsarConsumers);
        pulsarConsumers.addAll(createConsumers(pulsarEndpoint, consumerCreationStrategyFactory));
    }

    private Collection<Consumer<byte[]>> createConsumers(final PulsarEndpoint endpoint,
        final ConsumerCreationStrategyFactory factory) {

        ConsumerCreationStrategy strategy = factory
            .getStrategy(endpoint.getConfiguration().getSubscriptionType());

        return strategy.create(endpoint);
    }

}