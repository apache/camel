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
package org.apache.camel.component.redis.processor.aggregate.integration;

import org.apache.camel.component.redis.processor.aggregate.RedisAggregationRepository;
import org.junit.jupiter.api.Test;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RedisAggregationRepositoryTest {
    @Test
    public void testOptimisticRecoveryRejectsDifferentClusterSlots() throws Exception {
        RedissonClient redisson = mock(RedissonClient.class);
        Config config = mock(Config.class);
        RKeys keys = mock(RKeys.class);

        when(redisson.getConfig()).thenReturn(config);
        when(config.isClusterConfig()).thenReturn(true);
        when(redisson.getKeys()).thenReturn(keys);

        when(keys.getSlot("orders")).thenReturn(100);
        when(keys.getSlot("orders-completed")).thenReturn(200);

        RedisAggregationRepository repository = new RedisAggregationRepository();
        repository.setRedisson(redisson);
        repository.setMapName("orders");
        repository.setPersistenceMapName("orders-completed");
        repository.setOptimistic(true);
        repository.setUseRecovery(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                repository::start);

        assertTrue(exception.getMessage().contains("same hash slot"));
    }
}
