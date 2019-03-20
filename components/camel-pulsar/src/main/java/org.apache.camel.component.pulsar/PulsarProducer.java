package org.apache.camel.component.pulsar;

import java.util.Objects;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.camel.support.DefaultProducer;
import org.apache.pulsar.client.api.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarProducer extends DefaultProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarProducer.class);

    private Producer<byte[]> producer;
    private final PulsarEndpoint pulsarEndpoint;

    public PulsarProducer(PulsarEndpoint pulsarEndpoint) {
        super(pulsarEndpoint);
        this.pulsarEndpoint = pulsarEndpoint;
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final PulsarEndpointConfiguration configuration = pulsarEndpoint.getConfiguration();
        final Message message = exchange.getIn();

        final String producerName = configuration.getTopic() + "-" + configuration.getSubscriptionName();


        if(Objects.isNull(producer)) {
            producer = pulsarEndpoint.getPulsarClient()
                .newProducer()
                .topic(configuration.getTopic())
                .producerName(producerName)
                .create();
        }

        try {
            byte[] body = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, message);

            producer.sendAsync(body);
        } catch (NoTypeConversionAvailableException | TypeConversionException exception) {
            LOGGER.error("", exception);
        }
    }
}
