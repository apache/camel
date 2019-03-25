package org.apache.camel.component.pulsar;

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

    private Producer<byte[]> producer;
    private final PulsarEndpoint pulsarEndpoint;

    private PulsarProducer(PulsarEndpoint pulsarEndpoint) {
        super(pulsarEndpoint);
        this.pulsarEndpoint = pulsarEndpoint;
    }

    public static PulsarProducer create(final PulsarEndpoint pulsarEndpoint) {
        return new PulsarProducer(pulsarEndpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final PulsarEndpointConfiguration configuration = pulsarEndpoint.getConfiguration();
        final Message message = exchange.getIn();

        if (producer == null) {
            producer = createProducer(configuration);
        }

        try {
            byte[] body = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, message);

            producer.sendAsync(body);
        } catch (NoTypeConversionAvailableException | TypeConversionException exception) {
            LOGGER.error("", exception);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        producer = createProducer(pulsarEndpoint.getConfiguration());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (producer != null && !producer.isConnected()) {
            producer.close();
        }
    }

    private Producer<byte[]> createProducer(final PulsarEndpointConfiguration configuration) throws PulsarClientException {
        return pulsarEndpoint.getPulsarClient()
            .newProducer()
            .topic(pulsarEndpoint.getTopic())
            .producerName(configuration.getProducerName())
            .create();
    }
}
