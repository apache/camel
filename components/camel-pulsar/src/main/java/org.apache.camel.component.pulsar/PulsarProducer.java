package org.apache.camel.component.pulsar;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.camel.impl.DefaultProducer;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarProducer extends DefaultProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarProducer.class);

    private final Queue<Producer<byte[]>> producers;
    private final PulsarEndpoint pulsarEndpoint;

    private PulsarProducer(PulsarEndpoint pulsarEndpoint) {
        super(pulsarEndpoint);

        this.pulsarEndpoint = pulsarEndpoint;
        this.producers = new LinkedList<>();
    }

    public static PulsarProducer create(final PulsarEndpoint pulsarEndpoint) {
        return new PulsarProducer(pulsarEndpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        synchronized (this) {
            final Message message = exchange.getIn();
            final Producer<byte[]> producer = producers.peek();

            try {
                byte[] body = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, exchange, message.getBody());
                producer.send(body);
            } catch (NoTypeConversionAvailableException | TypeConversionException exception) {
                producer.send(message.getBody(byte[].class));
                LOGGER.error("An error occurred while serializing to byte array :: {}", exception);
            }
        }
    }

    @Override
    protected synchronized void doStart() throws Exception {
        super.doStart();

        stopProducer(producers);
        producers.add(createProducer(pulsarEndpoint.getConfiguration()));
    }


    @Override
    protected void doStop() throws Exception {
        super.doStop();

        stopProducer(producers);
    }

    @Override
    protected void doSuspend() throws Exception {
        super.doSuspend();

        stopProducer(producers);
    }

    @Override
    protected void doResume() throws Exception {
        super.doResume();

        producers.add(createProducer(pulsarEndpoint.getConfiguration()));
    }

    private Producer<byte[]> createProducer(final PulsarEndpointConfiguration configuration) throws PulsarClientException {
        return pulsarEndpoint.getPulsarClient()
            .newProducer()
            .topic(pulsarEndpoint.getTopic())
            .producerName(configuration.getProducerName())
            .create();
    }

    private void stopProducer(final Collection<Producer<byte[]>> pulsarProducers) throws PulsarClientException {
        while (!pulsarProducers.isEmpty()) {
            Producer<byte[]> producer = producers.poll();
            producer.close();
        }
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
