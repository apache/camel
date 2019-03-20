package org.apache.camel.component.pulsar.utils.consumers;

import org.apache.camel.component.pulsar.PulsarConsumer;
import org.apache.camel.component.pulsar.PulsarEndpoint;
import org.apache.camel.component.pulsar.PulsarMessageListener;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.pulsar.client.api.ConsumerBuilder;

public class CommonCreationStrategy {

    public ConsumerBuilder<byte[]> create(final String name, final PulsarEndpoint pulsarEndpoint, final PulsarConsumer pulsarConsumer) {
        final PulsarEndpointConfiguration endpointConfiguration = pulsarEndpoint.getConfiguration();

        return pulsarEndpoint
            .getPulsarClient()
            .newConsumer()
            .topic(endpointConfiguration.getTopic())
            .subscriptionName(endpointConfiguration.getSubscriptionName())
            .receiverQueueSize(endpointConfiguration.getConsumerQueueSize())
            .consumerName(name)
            .messageListener(new PulsarMessageListener(pulsarConsumer, pulsarEndpoint));
    }
}
