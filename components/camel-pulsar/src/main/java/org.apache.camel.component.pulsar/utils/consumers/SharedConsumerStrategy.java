package org.apache.camel.component.pulsar.utils.consumers;

import java.util.Collection;
import java.util.LinkedList;
import org.apache.camel.component.pulsar.PulsarConsumer;
import org.apache.camel.component.pulsar.PulsarEndpoint;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SubscriptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharedConsumerStrategy implements ConsumerCreationStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(SharedConsumerStrategy.class);

    private final PulsarConsumer pulsarConsumer;
    private final CommonCreationStrategy commonCreationStrategy;

    public SharedConsumerStrategy(PulsarConsumer pulsarConsumer) {
        this.pulsarConsumer = pulsarConsumer;
        commonCreationStrategy = new CommonCreationStrategy();
    }

    @Override
    public Collection<Consumer<byte[]>> create(final PulsarEndpoint pulsarEndpoint) {
        final Collection<Consumer<byte[]>> consumers = new LinkedList<>();
        final PulsarEndpointConfiguration configuration = pulsarEndpoint.getConfiguration();

        for (int i = 0; i < configuration.getNumberOfConsumers(); i++) {
            try {
                String consumerName = configuration.getConsumerNamePrefix() + i;

                ConsumerBuilder<byte[]> builder = commonCreationStrategy.create(consumerName, pulsarEndpoint, pulsarConsumer);

                consumers.add(builder.subscriptionType(SubscriptionType.Shared).subscribe());

                LOGGER.info("created consumer :- {}", consumerName);
            } catch (PulsarClientException exception) {
                // retry Logic in background thread
                LOGGER.error("", exception);
            }
        }

        return consumers;
    }
}
