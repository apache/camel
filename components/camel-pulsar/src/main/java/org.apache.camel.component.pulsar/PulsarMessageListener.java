package org.apache.camel.component.pulsar;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageUtils;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarMessageListener implements MessageListener<byte[]> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarMessageListener.class);
    private final PulsarEndpoint endpoint;
    private final ExceptionHandler exceptionHandler;
    private final Processor processor;

    public PulsarMessageListener(PulsarEndpoint endpoint, ExceptionHandler exceptionHandler, Processor processor) {
        this.endpoint = endpoint;
        this.exceptionHandler = exceptionHandler;
        this.processor = processor;
    }

    @Override
    public void received(final Consumer<byte[]> consumer, final Message<byte[]> message) {
        final Exchange exchange = PulsarMessageUtils.updateExchange(message, endpoint.createExchange());

        try {
            processor.process(exchange);
        } catch (Exception exception) {
            handleProcessorException(exchange, exception);
        } finally {
            acknowledgeReceipt(consumer, message);
        }
    }

    private void handleProcessorException(final Exchange exchange, final Exception exception) {
        final Exchange exchangeWithException = PulsarMessageUtils
            .updateExchangeWithException(exception, exchange);

        exceptionHandler
            .handleException("An error occurred", exchangeWithException, exception);

        LOGGER.error("An error occurred while processing this exchange :: {}", exception);
    }

    private void acknowledgeReceipt(final Consumer<byte[]> consumer, final Message<byte[]> message) {
        try {
            consumer.acknowledge(message.getMessageId());
        } catch (PulsarClientException exception) {
            // retry acknowledge
            LOGGER.error("An error occurred while acknowledging this message :: {}", exception);
        }
    }
}
