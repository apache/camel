package org.apache.camel.component.pulsar.utils.consumers;

import java.util.Collection;
import java.util.Collections;
import org.apache.camel.component.pulsar.PulsarConsumer;
import org.apache.camel.component.pulsar.PulsarEndpoint;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SubscriptionType;

public class ExclusiveConsumerStrategy implements ConsumerCreationStrategy {

    private final PulsarConsumer pulsarConsumer;
    private final CommonCreationStrategy commonCreationStrategy;

    public ExclusiveConsumerStrategy(PulsarConsumer pulsarConsumer) {
        this.pulsarConsumer = pulsarConsumer;
        commonCreationStrategy = new CommonCreationStrategy();
    }

    @Override
    public Collection<Consumer<byte[]>> create(final PulsarEndpoint pulsarEndpoint) {
        String consumerName = pulsarEndpoint.getConfiguration().getConsumerName();

        ConsumerBuilder<byte[]> builder = commonCreationStrategy.create(consumerName, pulsarEndpoint, pulsarConsumer);

        try {
            return Collections.singletonList(builder.subscriptionType(SubscriptionType.Exclusive).subscribe());
        } catch (PulsarClientException exception) {
            // retry In the background
            return Collections.emptyList();
        }
    }
}
