package org.apache.camel.component.pulsar.utils.consumers;

import static org.mockito.Mockito.mock;

import org.apache.camel.component.pulsar.PulsarConsumer;
import org.apache.camel.component.pulsar.utils.retry.PulsarClientRetryPolicy;
import org.junit.Test;

public class ExclusiveConsumerStrategyTest {

    @Test
    public void test() {
        PulsarConsumer mockConsumer = mock(PulsarConsumer.class);
        PulsarClientRetryPolicy mockRetryPolicy = mock(PulsarClientRetryPolicy.class);

        ConsumerCreationStrategy strategy = new ExclusiveConsumerStrategy(mockConsumer, mockRetryPolicy);

        strategy.create(null);
    }
}