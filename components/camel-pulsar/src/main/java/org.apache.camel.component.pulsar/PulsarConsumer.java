package org.apache.camel.component.pulsar;

import static org.apache.camel.component.pulsar.utils.PulsarUtils.createConsumers;
import static org.apache.camel.component.pulsar.utils.PulsarUtils.stopConsumers;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.camel.Processor;
import org.apache.camel.component.pulsar.utils.consumers.ConsumerCreationStrategyFactory;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarConsumer extends DefaultConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarConsumer.class);

    private final PulsarEndpoint pulsarEndpoint;
    private final ConsumerCreationStrategyFactory consumerCreationStrategyFactory;

    private Queue<Consumer<byte[]>> pulsarConsumers;

    private PulsarConsumer(PulsarEndpoint pulsarEndpoint, Processor processor) {
        super(pulsarEndpoint, processor);
        this.pulsarEndpoint = pulsarEndpoint;
        this.pulsarConsumers = new ConcurrentLinkedQueue<>();
        this.consumerCreationStrategyFactory = new ConsumerCreationStrategyFactory(this);
    }

    public static PulsarConsumer create(PulsarEndpoint pulsarEndpoint, Processor processor) {
        if (pulsarEndpoint == null || processor == null) {
            IllegalArgumentException illegalArgumentException = new IllegalArgumentException("Pulsar endpoint nor processor must be supplied");
            LOGGER.error("An exception occurred when creating PulsarConsumer :: {}", illegalArgumentException);
            throw illegalArgumentException;
        }
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
}