package org.apache.camel.component.pulsar;

import org.apache.camel.Exchange;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageUtils;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarMessageListener implements MessageListener<byte[]> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarMessageListener.class);

    private final PulsarConsumer pulsarCamelConsumer;
    private final PulsarEndpoint endpoint;

    public PulsarMessageListener(PulsarConsumer pulsarCamelConsumer, PulsarEndpoint endpoint) {
        this.pulsarCamelConsumer = pulsarCamelConsumer;
        this.endpoint = endpoint;
    }

    @Override
    public void received(final Consumer<byte[]> consumer, final Message<byte[]> message) {
        final Exchange exchange = PulsarMessageUtils.updateExchange(message, endpoint.createExchange());

        try {
            pulsarCamelConsumer.getProcessor().process(exchange);

        } catch (Exception exception) {
            exchange.setException(exception);
            pulsarCamelConsumer.getExceptionHandler().handleException("", exchange, exception);
            LOGGER.error("", exception);
        } finally {
            try {
                consumer.acknowledge(message.getMessageId());
            } catch (PulsarClientException exception) {
                LOGGER.error("", exception);
            }
        }
    }
}
