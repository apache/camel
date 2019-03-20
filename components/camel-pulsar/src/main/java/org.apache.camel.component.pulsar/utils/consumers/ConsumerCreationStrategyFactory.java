package org.apache.camel.component.pulsar.utils.consumers;

import org.apache.camel.component.pulsar.PulsarConsumer;

public class ConsumerCreationStrategyFactory {

    private final ExclusiveConsumerStrategy exclusiveConsumerStrategy;
    private final SharedConsumerStrategy sharedConsumerStrategy;
    private final PulsarConsumer pulsarConsumer;

    public ConsumerCreationStrategyFactory(PulsarConsumer pulsarConsumer) {
        this.pulsarConsumer = pulsarConsumer;
        sharedConsumerStrategy = new SharedConsumerStrategy(this.pulsarConsumer);
        exclusiveConsumerStrategy = new ExclusiveConsumerStrategy(this.pulsarConsumer);
    }


    public ConsumerCreationStrategy getStrategy(final SubscriptionType subscriptionType) {
        switch (subscriptionType) {
            case SHARED:
                return sharedConsumerStrategy;
            case EXCLUSIVE:
                return exclusiveConsumerStrategy;
            default:
                return exclusiveConsumerStrategy;
        }
    }
}
