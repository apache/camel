package org.apache.camel.component.pulsar;

import org.apache.camel.Exchange;
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
    public void received(final Consumer<byte[]> pulsarConsumer, final Message<byte[]> message) {
        final Exchange exchange = endpoint.createExchange();

        try {
            LOGGER.info("Consumed message -> {} on thread {}", message, Thread.currentThread());

            org.apache.camel.Message msg = exchange.getIn();

            msg.setBody(message.getValue());

            pulsarCamelConsumer.getProcessor().process(exchange);

        } catch (Exception exception) {
            exchange.setException(exception);
            pulsarCamelConsumer.getExceptionHandler().handleException("", exchange, exception);
            LOGGER.error("", exception);
        } finally {
            try {
                pulsarConsumer.acknowledge(message.getMessageId());
            } catch (PulsarClientException exception) {
                LOGGER.error("", exception);
            }
        }
    }
}
