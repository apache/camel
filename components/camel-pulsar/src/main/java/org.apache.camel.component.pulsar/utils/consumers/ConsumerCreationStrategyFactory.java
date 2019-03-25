package org.apache.camel.component.pulsar.utils.consumers;

import org.apache.camel.component.pulsar.PulsarConsumer;
import org.apache.camel.component.pulsar.utils.retry.PulsarClientRetryPolicy;

public class ConsumerCreationStrategyFactory {

    private final PulsarClientRetryPolicy retryPolicy;
    private final PulsarConsumer pulsarConsumer;

    private ConsumerCreationStrategyFactory(PulsarConsumer pulsarConsumer, PulsarClientRetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
        this.pulsarConsumer = pulsarConsumer;
    }

    public static ConsumerCreationStrategyFactory create(PulsarConsumer pulsarConsumer, PulsarClientRetryPolicy retryPolicy) {
        validate(pulsarConsumer, retryPolicy);
        return new ConsumerCreationStrategyFactory(pulsarConsumer, retryPolicy);
    }

    private static void validate(PulsarConsumer pulsarConsumer, PulsarClientRetryPolicy retryPolicy) {
        if (pulsarConsumer == null || retryPolicy == null) {
            throw new IllegalArgumentException("Neither Pulsar Consumer nor Retry Policy can be null");
        }
    }


    public ConsumerCreationStrategy getStrategy(final SubscriptionType subscriptionType) {
        final SubscriptionType type = subscriptionType == null ? SubscriptionType.EXCLUSIVE : subscriptionType;

        switch (type) {
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
