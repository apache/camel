package org.apache.camel.component.pulsar.utils.consumers;

import org.apache.camel.component.pulsar.PulsarConsumer;
import org.apache.camel.component.pulsar.utils.retry.PulsarClientRetryPolicy;

public class ConsumerCreationStrategyFactory {

    private final PulsarClientRetryPolicy retryPolicy;
    private final PulsarConsumer pulsarConsumer;

    public ConsumerCreationStrategyFactory(PulsarConsumer pulsarConsumer, PulsarClientRetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
        this.pulsarConsumer = pulsarConsumer;
    }


    public ConsumerCreationStrategy getStrategy(final SubscriptionType subscriptionType) {
        switch (subscriptionType) {
            case SHARED:
                return new SharedConsumerStrategy(pulsarConsumer, retryPolicy);
            case EXCLUSIVE:
                return new ExclusiveConsumerStrategy(pulsarConsumer, retryPolicy);
            case FAILOVER:
                return new FailoverConsumerStrategy(pulsarConsumer, retryPolicy);
            default:
                return new ExclusiveConsumerStrategy(pulsarConsumer, retryPolicy);
        }
    }
}
