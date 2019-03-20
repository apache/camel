package org.apache.camel.component.pulsar.utils.consumers;

import org.apache.camel.component.pulsar.PulsarConsumer;

public class ConsumerCreationStrategyFactory {

    private final ExclusiveConsumerStrategy exclusiveConsumerStrategy;
    private final SharedConsumerStrategy sharedConsumerStrategy;
    private final FailoverConsumerStrategy failoverConsumerStrategy;


    public ConsumerCreationStrategyFactory(PulsarConsumer pulsarConsumer) {
        sharedConsumerStrategy = new SharedConsumerStrategy(pulsarConsumer);
        exclusiveConsumerStrategy = new ExclusiveConsumerStrategy(pulsarConsumer);
        failoverConsumerStrategy = new FailoverConsumerStrategy(pulsarConsumer);
    }


    public ConsumerCreationStrategy getStrategy(final SubscriptionType subscriptionType) {
        switch (subscriptionType) {
            case SHARED:
                return sharedConsumerStrategy;
            case EXCLUSIVE:
                return exclusiveConsumerStrategy;
            case FAILOVER:
                return failoverConsumerStrategy;
            default:
                return exclusiveConsumerStrategy;
        }
    }
}
