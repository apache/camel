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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RedisIdempotentRepositoryTest {

    private static final String REPOSITORY = "testRepository";
    private static final String KEY = "KEY";

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private RedisConnectionFactory redisConnectionFactory;
    @Mock
    private RedisConnection redisConnection;
    @Mock
    private SetOperations<String, String> setOperations;

    private RedisIdempotentRepository idempotentRepository;

    @Before
    public void setUp() throws Exception {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);
        when(redisTemplate.getConnectionFactory().getConnection()).thenReturn(redisConnection);
        idempotentRepository = RedisIdempotentRepository.redisIdempotentRepository(redisTemplate, REPOSITORY);
    }

    @Test
    public void shouldAddKey() {
        idempotentRepository.add(KEY);
        verify(setOperations).add(REPOSITORY, KEY);
    }

    @Test
    public void shoulCheckForMembers() {
        idempotentRepository.contains(KEY);
        verify(setOperations).isMember(REPOSITORY, KEY);
    }

    @Test
    public void shouldRemoveKey() {
        idempotentRepository.remove(KEY);
        verify(setOperations).remove(REPOSITORY, KEY);
    }

    @Test
    public void shouldClearRepository() {
        idempotentRepository.clear();
        verify(redisConnection).flushDb();
    }

    @Test
    public void shouldReturnProcessorName() {
        String processorName = idempotentRepository.getProcessorName();
        assertEquals(REPOSITORY, processorName);
    }
}
