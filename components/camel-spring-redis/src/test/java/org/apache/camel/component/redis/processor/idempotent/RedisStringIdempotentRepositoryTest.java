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
package org.apache.camel.component.redis.processor.idempotent;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RedisStringIdempotentRepositoryTest {

    private static final String REPOSITORY = "testRepository";
    private static final String KEY = "KEY";
    private RedisTemplate redisTemplate;
    private RedisConnectionFactory redisConnectionFactory;
    private RedisConnection redisConnection;
    private RedisOperations redisOperations;
    private ValueOperations valueOperations;
    private RedisStringIdempotentRepository idempotentRepository;

    @Before
    public void setUp() throws Exception {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        redisConnection = mock(RedisConnection.class);
        redisOperations = mock(RedisOperations.class);
        redisConnectionFactory = mock(RedisConnectionFactory.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);
        when(valueOperations.getOperations()).thenReturn(redisOperations);
        when(redisTemplate.getConnectionFactory().getConnection()).thenReturn(redisConnection);
        idempotentRepository = new RedisStringIdempotentRepository(redisTemplate, REPOSITORY);
        idempotentRepository.setExpiry(1000L);
    }

    @Test
    public void shouldAddKey() {
        idempotentRepository.add(KEY);
        verify(valueOperations).setIfAbsent(idempotentRepository.createRedisKey(KEY), KEY);
        verify(redisOperations)
                .expire(idempotentRepository.createRedisKey(KEY), 1000L, TimeUnit.SECONDS);
    }

    @Test
    public void shoulCheckForMembers() {
        idempotentRepository.contains(KEY);
        verify(valueOperations).get(idempotentRepository.createRedisKey(KEY));
    }


    @Test
    public void shouldReturnProcessorName() {
        String processorName = idempotentRepository.getProcessorName();
        assertEquals(REPOSITORY, processorName);
    }

}
