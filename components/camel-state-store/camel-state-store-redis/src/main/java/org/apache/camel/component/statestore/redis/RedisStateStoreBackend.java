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
package org.apache.camel.component.statestore.redis;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.statestore.StateStoreBackend;
import org.redisson.Redisson;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * A {@link StateStoreBackend} implementation backed by Redis using Redisson. Uses {@link RMapCache} for per-entry TTL
 * support.
 */
public class RedisStateStoreBackend implements StateStoreBackend {

    private RedissonClient redisson;
    private RMapCache<String, Object> mapCache;
    private String redisUrl = "redis://localhost:6379";
    private String mapName = "camel-state-store";
    private boolean managedRedisson;

    @Override
    public Object put(String key, Object value, long ttlMillis) {
        if (ttlMillis > 0) {
            return mapCache.put(key, value, ttlMillis, TimeUnit.MILLISECONDS);
        }
        return mapCache.put(key, value);
    }

    @Override
    public Object putIfAbsent(String key, Object value, long ttlMillis) {
        if (ttlMillis > 0) {
            return mapCache.putIfAbsent(key, value, ttlMillis, TimeUnit.MILLISECONDS);
        }
        return mapCache.putIfAbsent(key, value);
    }

    @Override
    public Object get(String key) {
        return mapCache.get(key);
    }

    @Override
    public Object delete(String key) {
        return mapCache.remove(key);
    }

    @Override
    public boolean contains(String key) {
        return mapCache.containsKey(key);
    }

    @Override
    public Set<String> keys() {
        return Set.copyOf(mapCache.keySet());
    }

    @Override
    public int size() {
        return mapCache.size();
    }

    @Override
    public void clear() {
        mapCache.clear();
    }

    @Override
    public void start() {
        if (redisson == null) {
            Config config = new Config();
            config.useSingleServer().setAddress(redisUrl);
            redisson = Redisson.create(config);
            managedRedisson = true;
        }
        mapCache = redisson.getMapCache(mapName);
    }

    @Override
    public void stop() {
        if (managedRedisson && redisson != null) {
            redisson.shutdown();
            redisson = null;
        }
    }

    public RedissonClient getRedisson() {
        return redisson;
    }

    public void setRedisson(RedissonClient redisson) {
        this.redisson = redisson;
    }

    public String getRedisUrl() {
        return redisUrl;
    }

    public void setRedisUrl(String redisUrl) {
        this.redisUrl = redisUrl;
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }
}
