package org.apache.camel.component.pulsar.utils.message;

import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.EVENT_TIME;
import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.KEY;
import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.KEY_BYTES;
import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.MESSAGE_ID;
import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.PRODUCER_NAME;
import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.PROPERTIES;
import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.PUBLISH_TIME;
import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.SEQUENCE_ID;
import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.TOPIC_NAME;

import org.apache.camel.Exchange;
import org.apache.pulsar.client.api.Message;

public final class PulsarMessageUtils {

    public static Exchange updateExchange(final Message<byte[]> message, final Exchange input) {
        final Exchange output = input.copy(true);

        org.apache.camel.Message msg = output.getIn();

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

        output.setIn(msg);

        return output;
    }

    public static Exchange updateExchangeWithException(final Exception exception, final Exchange input) {
        final Exchange output = input.copy(true);

        output.setException(exception);

        return output;
    }
}
