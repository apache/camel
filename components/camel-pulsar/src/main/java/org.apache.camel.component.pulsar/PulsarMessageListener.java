package org.apache.camel.component.pulsar;

import static org.apache.camel.component.pulsar.utils.PulsarMessageHeaders.EVENT_TIME;
import static org.apache.camel.component.pulsar.utils.PulsarMessageHeaders.KEY;
import static org.apache.camel.component.pulsar.utils.PulsarMessageHeaders.KEY_BYTES;
import static org.apache.camel.component.pulsar.utils.PulsarMessageHeaders.MESSAGE_ID;
import static org.apache.camel.component.pulsar.utils.PulsarMessageHeaders.PRODUCER_NAME;
import static org.apache.camel.component.pulsar.utils.PulsarMessageHeaders.PROPERTIES;
import static org.apache.camel.component.pulsar.utils.PulsarMessageHeaders.PUBLISH_TIME;
import static org.apache.camel.component.pulsar.utils.PulsarMessageHeaders.SEQUENCE_ID;
import static org.apache.camel.component.pulsar.utils.PulsarMessageHeaders.TOPIC_NAME;

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
            org.apache.camel.Message msg = exchange.getIn();

            msg.setHeader(EVENT_TIME, message.getEventTime());
            msg.setHeader(MESSAGE_ID, message.getMessageId());
            msg.setHeader(KEY, message.getKey());
            msg.setHeader(KEY_BYTES, message.getKeyBytes());
            msg.setHeader(PRODUCER_NAME, message.getProducerName());
            msg.setHeader(TOPIC_NAME, message.getTopicName());
            msg.setHeader(SEQUENCE_ID, message.getSequenceId());
            msg.setHeader(PUBLISH_TIME, message.getPublishTime());
            msg.setHeader(PROPERTIES, message.getProperties());

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
