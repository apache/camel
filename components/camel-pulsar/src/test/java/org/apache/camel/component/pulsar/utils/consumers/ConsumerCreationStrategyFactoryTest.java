/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.pulsar.utils.consumers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import org.apache.camel.component.pulsar.PulsarConsumer;
import org.apache.camel.component.pulsar.utils.retry.PulsarClientRetryPolicy;
import org.junit.Test;

public class ConsumerCreationStrategyFactoryTest {

    @Test(expected = IllegalArgumentException.class)
    public void givenPulsarConsumerIsNull_whenICreateFactory_verifyIllegalArgumentExceptionIsThrown() {
        ConsumerCreationStrategyFactory.create(null, mock(PulsarClientRetryPolicy.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenRetryPolicyIsNull_whenICreateFactory_verifyIllegalArgumentExceptionIsThrown() {
        ConsumerCreationStrategyFactory.create(mock(PulsarConsumer.class), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenPulsarConsumerAndRetryPolicyIsNull_whenICreateFactory_verifyIllegalArgumentExceptionIsThrown() {
        ConsumerCreationStrategyFactory.create(null, null);
    }

    @Test()
    public void givenPulsarConsumerAndRetryPolicyNonNull_whenICreateFactory_verifyIllegalArgumentExceptionIsThrown() {
        ConsumerCreationStrategyFactory factory = ConsumerCreationStrategyFactory.create(mock(PulsarConsumer.class),
            mock(PulsarClientRetryPolicy.class));

        assertNotNull(factory);
    }

    @Test
    public void verifyFailOverStrategy() {
        ConsumerCreationStrategyFactory factory = ConsumerCreationStrategyFactory.create(mock(PulsarConsumer.class),
            mock(PulsarClientRetryPolicy.class));

        ConsumerCreationStrategy strategy = factory.getStrategy(SubscriptionType.FAILOVER);

        assertEquals(FailoverConsumerStrategy.class, strategy.getClass());
    }

    @Test
    public void verifySharedStrategy() {
        ConsumerCreationStrategyFactory factory = ConsumerCreationStrategyFactory.create(mock(PulsarConsumer.class),
            mock(PulsarClientRetryPolicy.class));

        ConsumerCreationStrategy strategy = factory.getStrategy(SubscriptionType.SHARED);

        assertEquals(SharedConsumerStrategy.class, strategy.getClass());
    }

    @Test
    public void verifyExclusiveStrategy() {
        ConsumerCreationStrategyFactory factory = ConsumerCreationStrategyFactory.create(mock(PulsarConsumer.class),
            mock(PulsarClientRetryPolicy.class));

        ConsumerCreationStrategy strategy = factory.getStrategy(SubscriptionType.EXCLUSIVE);

        assertEquals(ExclusiveConsumerStrategy.class, strategy.getClass());
    }

    @Test
    public void verifyDefaultStrategyIsExclusiveStrategy() {
        ConsumerCreationStrategyFactory factory = ConsumerCreationStrategyFactory.create(mock(PulsarConsumer.class),
            mock(PulsarClientRetryPolicy.class));

        ConsumerCreationStrategy strategy = factory.getStrategy(null);

        assertEquals(ExclusiveConsumerStrategy.class, strategy.getClass());
    }
}