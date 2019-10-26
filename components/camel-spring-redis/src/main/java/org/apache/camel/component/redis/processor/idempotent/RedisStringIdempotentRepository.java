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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ValueOperations;

@ManagedResource(description = "Spring Redis based message id repository")
public class RedisStringIdempotentRepository extends RedisIdempotentRepository {

    private final ValueOperations<String, String> valueOperations;

    private long expiry;

    public RedisStringIdempotentRepository(RedisTemplate<String, String> redisTemplate, String processorName) {
        super(redisTemplate, processorName);
        this.valueOperations = redisTemplate.opsForValue();
    }

    @ManagedOperation(description = "Does the store contain the given key")
    @Override
    public boolean contains(String key) {
        String value = valueOperations.get(createRedisKey(key));
        return value != null;
    }

    @ManagedOperation(description = "Adds the key to the store")
    @Override
    public boolean add(String key) {
        if (expiry > 0) {
            return valueOperations.setIfAbsent(createRedisKey(key), key, Duration.ofSeconds(expiry));
        }
        return valueOperations.setIfAbsent(createRedisKey(key), key);
    }

    @ManagedOperation(description = "Remove the key from the store")
    @Override
    public boolean remove(String key) {
        valueOperations.getOperations().delete(createRedisKey(key));
        return true;
    }

    @ManagedOperation(description = "Clear the store")
    @Override
    public void clear() {
        valueOperations.getOperations().execute(new RedisCallback<List<byte[]>>() {
            @Override
            public List<byte[]> doInRedis(RedisConnection connection) throws DataAccessException {
                List<byte[]> binaryKeys = new ArrayList<>();
                Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match("*" + createRedisKey("*")).build());

                while (cursor.hasNext()) {
                    byte[] key = cursor.next();
                    binaryKeys.add(key);
                }
                if (binaryKeys.size() > 0) {
                    connection.del(binaryKeys.toArray(new byte[][]{}));
                }
                return binaryKeys;
            }
        });
    }

    protected String createRedisKey(String key) {
        return getProcessorName() + ":" + key;
    }

    public long getExpiry() {
        return expiry;
    }

    /**
     * Expire all newly added items after the given number of seconds (0 means never expire)
     */
    public void setExpiry(long expiry) {
        this.expiry = expiry;
    }
}
