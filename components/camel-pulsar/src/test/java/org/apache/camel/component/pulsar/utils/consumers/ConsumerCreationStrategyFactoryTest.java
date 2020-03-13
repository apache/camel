/*
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

import org.apache.camel.component.pulsar.PulsarConsumer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class ConsumerCreationStrategyFactoryTest {

    @Test(expected = IllegalArgumentException.class)
    public void givenPulsarConsumerIsNullwhenICreateFactoryverifyIllegalArgumentExceptionIsThrown() {
        ConsumerCreationStrategyFactory.create(null);
    }

    @Test
    public void givenPulsarConsumerAndRetryPolicyNonNullwhenICreateFactoryverifyIllegalArgumentExceptionIsNotThrown() {
        ConsumerCreationStrategyFactory factory = ConsumerCreationStrategyFactory.create(mock(PulsarConsumer.class));

        assertNotNull(factory);
    }

    @Test
    public void verifyFailOverStrategy() {
        ConsumerCreationStrategyFactory factory = ConsumerCreationStrategyFactory.create(mock(PulsarConsumer.class));

        ConsumerCreationStrategy strategy = factory.getStrategy(SubscriptionType.FAILOVER);

        assertEquals(FailoverConsumerStrategy.class, strategy.getClass());
    }

    @Test
    public void verifySharedStrategy() {
        ConsumerCreationStrategyFactory factory = ConsumerCreationStrategyFactory.create(mock(PulsarConsumer.class));

        ConsumerCreationStrategy strategy = factory.getStrategy(SubscriptionType.SHARED);

        assertEquals(SharedConsumerStrategy.class, strategy.getClass());
    }

    @Test
    public void verifyExclusiveStrategy() {
        ConsumerCreationStrategyFactory factory = ConsumerCreationStrategyFactory.create(mock(PulsarConsumer.class));

        ConsumerCreationStrategy strategy = factory.getStrategy(SubscriptionType.EXCLUSIVE);

        assertEquals(ExclusiveConsumerStrategy.class, strategy.getClass());
    }

    @Test
    public void verifyKeySharedStrategy() {
        ConsumerCreationStrategyFactory factory = ConsumerCreationStrategyFactory.create(mock(PulsarConsumer.class));

        ConsumerCreationStrategy strategy = factory.getStrategy(SubscriptionType.KEY_SHARED);

        assertEquals(KeySharedConsumerStrategy.class, strategy.getClass());
    }

    @Test
    public void verifyDefaultStrategyIsExclusiveStrategy() {
        ConsumerCreationStrategyFactory factory = ConsumerCreationStrategyFactory.create(mock(PulsarConsumer.class));

        ConsumerCreationStrategy strategy = factory.getStrategy(null);

        assertEquals(ExclusiveConsumerStrategy.class, strategy.getClass());
    }
}
