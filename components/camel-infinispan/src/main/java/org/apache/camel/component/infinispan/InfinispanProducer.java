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
package org.apache.camel.component.infinispan;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Message;
import org.apache.camel.component.infinispan.remote.InfinispanRemoteOperation;
import org.apache.camel.spi.InvokeOnHeader;
import org.apache.camel.support.HeaderSelectorProducer;
import org.apache.camel.util.ObjectHelper;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.query.dsl.Query;

public class InfinispanProducer extends HeaderSelectorProducer {
    private final String cacheName;
    private final InfinispanConfiguration configuration;
    private final InfinispanManager manager;

    public InfinispanProducer(InfinispanEndpoint endpoint, String cacheName, InfinispanManager manager, InfinispanConfiguration configuration) {
        super(endpoint, InfinispanConstants.OPERATION, () -> configuration.getOperationOrDefault().name(), false);

        this.cacheName = cacheName;
        this.configuration = configuration;
        this.manager = manager;
    }

    // ************************************
    // Operations
    // ************************************

    @InvokeOnHeader("PUT")
    void onPut(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Object key = message.getHeader(InfinispanConstants.KEY);
        final Object value = message.getHeader(InfinispanConstants.VALUE);
        final Object result;

        if (hasLifespan(message)) {
            long lifespan = message.getHeader(InfinispanConstants.LIFESPAN_TIME, long.class);
            TimeUnit timeUnit = message.getHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.class);

            if (hasMaxIdleTime(message)) {
                long maxIdle = message.getHeader(InfinispanConstants.MAX_IDLE_TIME, long.class);
                TimeUnit maxIdleTimeUnit = message.getHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.class);

                result = cache.put(key, value, lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
            } else {
                result = cache.put(key, value, lifespan, timeUnit);
            }
        } else {
            result = cache.put(key, value);
        }

        setResult(message, result);
    }

    @InvokeOnHeader("PUTASYNC")
    void onPutAsync(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Object key = message.getHeader(InfinispanConstants.KEY);
        final Object value = message.getHeader(InfinispanConstants.VALUE);
        final CompletableFuture<Object> result;

        if (hasLifespan(message)) {
            long lifespan = message.getHeader(InfinispanConstants.LIFESPAN_TIME, long.class);
            TimeUnit timeUnit = message.getHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.class);

            if (hasMaxIdleTime(message)) {
                long maxIdle = message.getHeader(InfinispanConstants.MAX_IDLE_TIME, long.class);
                TimeUnit maxIdleTimeUnit = message.getHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.class);

                result = cache.putAsync(key, value, lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
            } else {
                result = cache.putAsync(key, value, lifespan, timeUnit);
            }
        } else {
            result = cache.putAsync(key, value);
        }

        setResult(message, result);
    }

    @InvokeOnHeader("PUTALL")
    void onPutAll(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Map<Object, Object> map = message.getHeader(InfinispanConstants.MAP, Map.class);

        if (hasLifespan(message)) {
            long lifespan = message.getHeader(InfinispanConstants.LIFESPAN_TIME, long.class);
            TimeUnit timeUnit =  message.getHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.class);

            if (hasMaxIdleTime(message)) {
                long maxIdle = message.getHeader(InfinispanConstants.MAX_IDLE_TIME, long.class);
                TimeUnit maxIdleTimeUnit =  message.getHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.class);

                cache.putAll(map, lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
            } else {
                cache.putAll(map, lifespan, timeUnit);
            }
        } else {
            cache.putAll(map);
        }
    }

    @InvokeOnHeader("PUTALLASYNC")
    void onPutAllAsync(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Map<Object, Object> map = message.getHeader(InfinispanConstants.MAP, Map.class);
        final CompletableFuture<Void> result;

        if (hasLifespan(message)) {
            long lifespan = message.getHeader(InfinispanConstants.LIFESPAN_TIME, long.class);
            TimeUnit timeUnit =  message.getHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.class);

            if (hasMaxIdleTime(message)) {
                long maxIdle = message.getHeader(InfinispanConstants.MAX_IDLE_TIME, long.class);
                TimeUnit maxIdleTimeUnit =  message.getHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.class);

                result = cache.putAllAsync(map, lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
            } else {
                result = cache.putAllAsync(map, lifespan, timeUnit);
            }
        } else {
            result = cache.putAllAsync(map);
        }

        setResult(message, result);
    }

    @InvokeOnHeader("PUTIFABSENT")
    void onPutIfAbsent(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Object key = message.getHeader(InfinispanConstants.KEY);
        final Object value = message.getHeader(InfinispanConstants.VALUE);
        final Object result;

        if (hasLifespan(message)) {
            long lifespan = message.getHeader(InfinispanConstants.LIFESPAN_TIME, long.class);
            TimeUnit timeUnit =  message.getHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.class);

            if (hasMaxIdleTime(message)) {
                long maxIdle = message.getHeader(InfinispanConstants.MAX_IDLE_TIME, long.class);
                TimeUnit maxIdleTimeUnit =  message.getHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.class);

                result = cache.putIfAbsent(key, value, lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
            } else {
                result = cache.putIfAbsent(key, value, lifespan, timeUnit);
            }
        } else {
            result = cache.putIfAbsent(key, value);
        }

        setResult(message, result);
    }

    @InvokeOnHeader("PUTIFABSENTASYNC")
    void onPutIfAbsentAsync(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Object key = message.getHeader(InfinispanConstants.KEY);
        final Object value = message.getHeader(InfinispanConstants.VALUE);
        final CompletableFuture<Object> result;

        if (hasLifespan(message)) {
            long lifespan = message.getHeader(InfinispanConstants.LIFESPAN_TIME, long.class);
            TimeUnit timeUnit =  message.getHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.class);

            if (hasMaxIdleTime(message)) {
                long maxIdle = message.getHeader(InfinispanConstants.MAX_IDLE_TIME, long.class);
                TimeUnit maxIdleTimeUnit =  message.getHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.class);

                result = cache.putIfAbsentAsync(key, value, lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
            } else {
                result = cache.putIfAbsentAsync(key, value, lifespan, timeUnit);
            }
        } else {
            result = cache.putIfAbsentAsync(key, value);
        }

        setResult(message, result);
    }

    @InvokeOnHeader("GET")
    void onGet(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Object key = message.getHeader(InfinispanConstants.KEY);
        final Object result = cache.get(key);

        setResult(message, result);
    }

    @InvokeOnHeader("GETORDEFAULT")
    void onGetOrDefault(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Object key = message.getHeader(InfinispanConstants.KEY);
        final Object defaultValue = message.getHeader(InfinispanConstants.DEFAULT_VALUE);
        final Object result = cache.getOrDefault(key, defaultValue);

        setResult(message, result);
    }


    @InvokeOnHeader("CONTAINSKEY")
    void onContainsKey(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Object key = message.getHeader(InfinispanConstants.KEY);
        final Object result = cache.containsKey(key);

        setResult(message, result);
    }

    @InvokeOnHeader("CONTAINSVALUE")
    void onContainsValue(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Object value = message.getHeader(InfinispanConstants.VALUE);
        final Object result = cache.containsValue(value);

        setResult(message, result);
    }

    @InvokeOnHeader("REMOVE")
    void onRemove(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Object key = message.getHeader(InfinispanConstants.KEY);
        final Object value = message.getHeader(InfinispanConstants.VALUE);
        final Object result;

        if (ObjectHelper.isEmpty(value)) {
            result = cache.remove(key);
        } else {
            result = cache.remove(key, value);
        }

        setResult(message, result);
    }

    @InvokeOnHeader("REMOVEASYNC")
    void onRemoveAsync(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Object key = message.getHeader(InfinispanConstants.KEY);
        final Object value = message.getHeader(InfinispanConstants.VALUE);
        final CompletableFuture<Object> resultRemoveAsyncKey;
        final CompletableFuture<Boolean> resultRemoveAsyncKeyValue;

        if (ObjectHelper.isEmpty(value)) {
            resultRemoveAsyncKey = cache.removeAsync(key);
            setResult(message, resultRemoveAsyncKey);
        } else {
            resultRemoveAsyncKeyValue = cache.removeAsync(key, value);
            setResult(message, resultRemoveAsyncKeyValue);
        }
    }

    @InvokeOnHeader("REPLACE")
    void onReplace(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Object key = message.getHeader(InfinispanConstants.KEY);
        final Object value = message.getHeader(InfinispanConstants.VALUE);
        final Object oldValue = message.getHeader(InfinispanConstants.OLD_VALUE);
        final Object result;

        if (hasLifespan(message)) {
            long lifespan = message.getHeader(InfinispanConstants.LIFESPAN_TIME, long.class);
            TimeUnit timeUnit =  message.getHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.class);

            if (hasMaxIdleTime(message)) {
                long maxIdle = message.getHeader(InfinispanConstants.MAX_IDLE_TIME, long.class);
                TimeUnit maxIdleTimeUnit =  message.getHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.class);

                if (ObjectHelper.isEmpty(oldValue)) {
                    result = cache.replace(key, value, lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
                } else {
                    result = cache.replace(key, oldValue, value, lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
                }
            } else {
                if (ObjectHelper.isEmpty(oldValue)) {
                    result = cache.replace(key, value, lifespan, timeUnit);
                } else {
                    result = cache.replace(key, oldValue, value, lifespan, timeUnit);
                }
            }
        } else {
            if (ObjectHelper.isEmpty(oldValue)) {
                result = cache.replace(key, value);
            } else {
                result = cache.replace(key, oldValue, value);
            }
        }

        setResult(message, result);
    }

    @InvokeOnHeader("REPLACEASYNC")
    void onReplaceAsync(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Object key = message.getHeader(InfinispanConstants.KEY);
        final Object value = message.getHeader(InfinispanConstants.VALUE);
        final Object oldValue = message.getHeader(InfinispanConstants.OLD_VALUE);
        final CompletableFuture<Object> resultWithNewValue;
        final CompletableFuture<Boolean> resultWithNewAndOldValue;

        if (hasLifespan(message)) {
            long lifespan = message.getHeader(InfinispanConstants.LIFESPAN_TIME, long.class);
            TimeUnit timeUnit =  message.getHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.class);

            if (hasMaxIdleTime(message)) {
                long maxIdle = message.getHeader(InfinispanConstants.MAX_IDLE_TIME, long.class);
                TimeUnit maxIdleTimeUnit =  message.getHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.class);

                if (ObjectHelper.isEmpty(oldValue)) {
                    resultWithNewValue = cache.replaceAsync(key, value, lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
                    setResult(message, resultWithNewValue);
                } else {
                    resultWithNewAndOldValue = cache.replaceAsync(key, oldValue, value, lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
                    setResult(message, resultWithNewAndOldValue);
                }
            } else {
                if (ObjectHelper.isEmpty(oldValue)) {
                    resultWithNewValue = cache.replaceAsync(key, value, lifespan, timeUnit);
                    setResult(message, resultWithNewValue);
                } else {
                    resultWithNewAndOldValue = cache.replaceAsync(key, oldValue, value, lifespan, timeUnit);
                    setResult(message, resultWithNewAndOldValue);
                }
            }
        } else {
            if (ObjectHelper.isEmpty(oldValue)) {
                resultWithNewValue = cache.replaceAsync(key, value);
                setResult(message, resultWithNewValue);
            } else {
                resultWithNewAndOldValue = cache.replaceAsync(key, oldValue, value);
                setResult(message, resultWithNewAndOldValue);
            }
        }

    }

    @InvokeOnHeader("SIZE")
    void onSize(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Object result = cache.size();
        setResult(message, result);
    }

    @InvokeOnHeader("CLEAR")
    void onClear(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        cache.clear();
    }

    @InvokeOnHeader("CLEARASYNC")
    void onCLearAsync(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final CompletableFuture<Void> result = cache.clearAsync();

        setResult(message, result);
    }

    @InvokeOnHeader("QUERY")
    void onQuery(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);

        if (InfinispanUtil.isRemote(cache)) {
            final Query query = InfinispanRemoteOperation.buildQuery(configuration, cache, message);

            if (query != null) {
                setResult(message, query.list());
            }
        } else {
            throw new UnsupportedOperationException("Query is supported on remote cache only");
        }
    }

    @InvokeOnHeader("STATS")
    void onStats(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Object result = InfinispanUtil.asAdvanced(cache).getStats();

        setResult(message, result);
    }

    @InvokeOnHeader("COMPUTE")
    void onCompute(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Object key = message.getHeader(InfinispanConstants.KEY);
        final Object result = cache.compute(key, configuration.getRemappingFunction());
        setResult(message, result);
    }

    @InvokeOnHeader("COMPUTEASYNC")
    void onComputeAsync(Message message) {
        final BasicCache<Object, Object> cache = manager.getCache(message, this.cacheName);
        final Object key = message.getHeader(InfinispanConstants.KEY);
        final CompletableFuture<Object> result = cache.computeAsync(key, configuration.getRemappingFunction());
        setResult(message, result);
    }

    // ************************************
    // Helpers
    // ************************************

    private boolean hasLifespan(Message message) {
        return !InfinispanUtil.isHeaderEmpty(message, InfinispanConstants.LIFESPAN_TIME)
            && !InfinispanUtil.isHeaderEmpty(message, InfinispanConstants.LIFESPAN_TIME_UNIT);
    }

    private boolean hasMaxIdleTime(Message message) {
        return !InfinispanUtil.isHeaderEmpty(message, InfinispanConstants.MAX_IDLE_TIME)
            && !InfinispanUtil.isHeaderEmpty(message, InfinispanConstants.MAX_IDLE_TIME_UNIT);
    }

    private void setResult(Message message, Object result) {
        String resultHeader = message.getHeader(InfinispanConstants.RESULT_HEADER, configuration::getResultHeader, String.class);
        if (resultHeader != null) {
            message.setHeader(resultHeader, result);
        } else {
            message.setBody(result);
        }
    }
}
