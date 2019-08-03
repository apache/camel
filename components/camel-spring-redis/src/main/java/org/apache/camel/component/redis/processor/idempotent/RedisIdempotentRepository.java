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
package org.apache.camel.component.redis.processor.idempotent;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.redis.RedisConfiguration;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

@ManagedResource(description = "Spring Redis based message id repository")
public class RedisIdempotentRepository extends ServiceSupport implements IdempotentRepository {
    private final SetOperations<String, String> setOperations;
    private final String processorName;
    private RedisConfiguration redisConfiguration;
    private RedisTemplate<String, String> redisTemplate;

    public RedisIdempotentRepository(RedisTemplate<String, String> redisTemplate, String processorName) {
        this.setOperations = redisTemplate.opsForSet();
        this.processorName = processorName;
        this.redisTemplate = redisTemplate;
    }

    public RedisIdempotentRepository(String processorName) {
        redisConfiguration = new RedisConfiguration();
        RedisTemplate<String, String> redisTemplate = redisConfiguration.getRedisTemplate();
        this.redisTemplate = redisTemplate;
        this.setOperations = redisTemplate.opsForSet();
        redisTemplate.getConnectionFactory().getConnection().flushDb();
        this.processorName = processorName;
    }

    public static RedisIdempotentRepository redisIdempotentRepository(String processorName) {
        return new RedisIdempotentRepository(processorName);
    }

    public static RedisIdempotentRepository redisIdempotentRepository(
            RedisTemplate<String, String> redisTemplate, String processorName) {
        return new RedisIdempotentRepository(redisTemplate, processorName);
    }

    @Override
    @ManagedOperation(description = "Adds the key to the store")
    public boolean add(String key) {
        if (!contains(key)) { 
            return setOperations.add(processorName, key) != null;
        } else {
            return false;
        }
    }

    @Override
    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String key) {
        return setOperations.isMember(processorName, key);
    }

    @Override
    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(String key) {
        return setOperations.remove(processorName, key) != null;
    }
    
    @Override
    @ManagedOperation(description = "Clear the store")
    public void clear() {
        redisTemplate.getConnectionFactory().getConnection().flushDb();
    }

    @ManagedAttribute(description = "The processor name")
    public String getProcessorName() {
        return processorName;
    }

    @Override
    public boolean confirm(String key) {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        if (redisConfiguration != null) {
            redisConfiguration.stop();
        }
    }
}

