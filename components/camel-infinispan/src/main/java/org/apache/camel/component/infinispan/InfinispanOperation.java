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
package org.apache.camel.component.infinispan;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.infinispan.remote.InfinispanRemoteOperation;
import org.apache.camel.util.ObjectHelper;
import org.infinispan.Cache;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.util.concurrent.NotifyingFuture;
import org.infinispan.query.dsl.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.infinispan.InfinispanUtil.isHeaderEmpty;

public final class InfinispanOperation {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(InfinispanOperation.class);

    private InfinispanOperation() {
    }

    public static void process(Exchange exchange, InfinispanConfiguration configuration, BasicCache<Object, Object> cache) {
        final Message in = exchange.getIn();

        Operation operation = getOperation(in, configuration);
        operation.execute(
            configuration,
            exchange.getIn().getHeader(InfinispanConstants.IGNORE_RETURN_VALUES) != null
                ? cache
                : InfinispanUtil.ignoreReturnValuesCache(cache),
            in
        );
    }

    private static Operation getOperation(Message message, InfinispanConfiguration configuration) {
        String operation = message.getHeader(InfinispanConstants.OPERATION, String.class);
        if (operation == null) {
            if (configuration.hasCommand()) {
                operation = InfinispanConstants.OPERATION + configuration.getCommand();
            } else {
                operation = InfinispanConstants.PUT;
            }
        }
        LOGGER.trace("Operation: [{}]", operation);
        return Operation.fromOperation(operation);
    }

    private enum Operation {
        PUT {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                Object result;
                if (hasLifespan(message)) {
                    long lifespan = message.getHeader(InfinispanConstants.LIFESPAN_TIME, long.class);
                    TimeUnit timeUnit =  message.getHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.class);
                    if (hasMaxIdleTime(message)) {
                        long maxIdle = message.getHeader(InfinispanConstants.MAX_IDLE_TIME, long.class);
                        TimeUnit maxIdleTimeUnit =  message.getHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.class);
                        result = cache.put(getKey(message), getValue(message), lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
                    } else {
                        result = cache.put(getKey(message), getValue(message), lifespan, timeUnit);
                    }
                } else {
                    result = cache.put(getKey(message), getValue(message));
                }
                setResult(result, message);
            }
        }, PUTASYNC {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                NotifyingFuture result;
                if (hasLifespan(message)) {
                    long lifespan = message.getHeader(InfinispanConstants.LIFESPAN_TIME, long.class);
                    TimeUnit timeUnit =  message.getHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.class);
                    if (hasMaxIdleTime(message)) {
                        long maxIdle = message.getHeader(InfinispanConstants.MAX_IDLE_TIME, long.class);
                        TimeUnit maxIdleTimeUnit =  message.getHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.class);
                        result = cache.putAsync(getKey(message), getValue(message), lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
                    } else {
                        result = cache.putAsync(getKey(message), getValue(message), lifespan, timeUnit);
                    }
                } else {
                    result = cache.putAsync(getKey(message), getValue(message));
                }
                setResult(result, message);
            }
        }, PUTALL {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                if (hasLifespan(message)) {
                    long lifespan = message.getHeader(InfinispanConstants.LIFESPAN_TIME, long.class);
                    TimeUnit timeUnit =  message.getHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.class);
                    if (hasMaxIdleTime(message)) {
                        long maxIdle = message.getHeader(InfinispanConstants.MAX_IDLE_TIME, long.class);
                        TimeUnit maxIdleTimeUnit =  message.getHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.class);
                        cache.putAll(getMap(message), lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
                    } else {
                        cache.putAll(getMap(message), lifespan, timeUnit);
                    }
                } else {
                    cache.putAll(getMap(message));
                }
            }
        }, PUTALLASYNC {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                NotifyingFuture result;
                if (hasLifespan(message)) {
                    long lifespan = message.getHeader(InfinispanConstants.LIFESPAN_TIME, long.class);
                    TimeUnit timeUnit =  message.getHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.class);
                    if (hasMaxIdleTime(message)) {
                        long maxIdle = message.getHeader(InfinispanConstants.MAX_IDLE_TIME, long.class);
                        TimeUnit maxIdleTimeUnit =  message.getHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.class);
                        result = cache.putAllAsync(getMap(message), lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
                    } else {
                        result = cache.putAllAsync(getMap(message), lifespan, timeUnit);
                    }
                } else {
                    result = cache.putAllAsync(getMap(message));
                }
                setResult(result, message);
            }
        }, PUTIFABSENT {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                Object result;
                if (hasLifespan(message)) {
                    long lifespan = message.getHeader(InfinispanConstants.LIFESPAN_TIME, long.class);
                    TimeUnit timeUnit =  message.getHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.class);
                    if (hasMaxIdleTime(message)) {
                        long maxIdle = message.getHeader(InfinispanConstants.MAX_IDLE_TIME, long.class);
                        TimeUnit maxIdleTimeUnit =  message.getHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.class);
                        result = cache.putIfAbsent(getKey(message), getValue(message), lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
                    } else {
                        result = cache.putIfAbsent(getKey(message), getValue(message), lifespan, timeUnit);
                    }
                } else {
                    result = cache.putIfAbsent(getKey(message), getValue(message));
                }
                setResult(result, message);
            }
        }, PUTIFABSENTASYNC {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                NotifyingFuture result;
                if (hasLifespan(message)) {
                    long lifespan = message.getHeader(InfinispanConstants.LIFESPAN_TIME, long.class);
                    TimeUnit timeUnit =  message.getHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.class);
                    if (hasMaxIdleTime(message)) {
                        long maxIdle = message.getHeader(InfinispanConstants.MAX_IDLE_TIME, long.class);
                        TimeUnit maxIdleTimeUnit =  message.getHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.class);
                        result = cache.putIfAbsentAsync(getKey(message), getValue(message), lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
                    } else {
                        result = cache.putIfAbsentAsync(getKey(message), getValue(message), lifespan, timeUnit);
                    }
                } else {
                    result = cache.putIfAbsentAsync(getKey(message), getValue(message));
                }
                setResult(result, message);
            }
        }, GET {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                setResult(cache.get(getKey(message)), message);
            }
        }, CONTAINSKEY {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                setResult(cache.containsKey(getKey(message)), message);
            }
        }, CONTAINSVALUE {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                setResult(cache.containsValue(getValue(message)), message);
            }
        }, REMOVE {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                Object result;
                if (ObjectHelper.isEmpty(getValue(message))) {
                    result = cache.remove(getKey(message));
                } else {
                    result = cache.remove(getKey(message), getValue(message));
                }
                setResult(result, message);
            }
        }, REMOVEASYNC {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                NotifyingFuture result;
                if (ObjectHelper.isEmpty(getValue(message))) {
                    result = cache.removeAsync(getKey(message));
                } else {
                    result = cache.removeAsync(getKey(message), getValue(message));
                }
                setResult(result, message);
            }
        }, REPLACE {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                Object result;
                if (hasLifespan(message)) {
                    long lifespan = message.getHeader(InfinispanConstants.LIFESPAN_TIME, long.class);
                    TimeUnit timeUnit =  message.getHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.class);
                    if (hasMaxIdleTime(message)) {
                        long maxIdle = message.getHeader(InfinispanConstants.MAX_IDLE_TIME, long.class);
                        TimeUnit maxIdleTimeUnit =  message.getHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.class);
                        if (ObjectHelper.isEmpty(getOldValue(message))) {
                            result = cache.replace(getKey(message), getValue(message), lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
                        } else {
                            result = cache.replace(getKey(message), getOldValue(message), getValue(message), lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
                        }
                    } else {
                        if (ObjectHelper.isEmpty(getOldValue(message))) {
                            result = cache.replace(getKey(message), getValue(message), lifespan, timeUnit);
                        } else {
                            result = cache.replace(getKey(message), getOldValue(message), getValue(message), lifespan, timeUnit);
                        }
                    }
                } else {
                    if (ObjectHelper.isEmpty(getOldValue(message))) {
                        result = cache.replace(getKey(message), getValue(message));
                    } else {
                        result = cache.replace(getKey(message), getOldValue(message), getValue(message));
                    }
                }
                setResult(result, message);
            }
        }, REPLACEASYNC {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                NotifyingFuture result;
                if (hasLifespan(message)) {
                    long lifespan = message.getHeader(InfinispanConstants.LIFESPAN_TIME, long.class);
                    TimeUnit timeUnit =  message.getHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.class);
                    if (hasMaxIdleTime(message)) {
                        long maxIdle = message.getHeader(InfinispanConstants.MAX_IDLE_TIME, long.class);
                        TimeUnit maxIdleTimeUnit =  message.getHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.class);
                        if (ObjectHelper.isEmpty(getOldValue(message))) {
                            result = cache.replaceAsync(getKey(message), getValue(message), lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
                        } else {
                            result = cache.replaceAsync(getKey(message), getOldValue(message), getValue(message), lifespan, timeUnit, maxIdle, maxIdleTimeUnit);
                        }
                    } else {
                        if (ObjectHelper.isEmpty(getOldValue(message))) {
                            result = cache.replaceAsync(getKey(message), getValue(message), lifespan, timeUnit);
                        } else {
                            result = cache.replaceAsync(getKey(message), getOldValue(message), getValue(message), lifespan, timeUnit);
                        }
                    }
                } else {
                    if (ObjectHelper.isEmpty(getOldValue(message))) {
                        result = cache.replaceAsync(getKey(message), getValue(message));
                    } else {
                        result = cache.replaceAsync(getKey(message), getOldValue(message), getValue(message));
                    }
                }
                setResult(result, message);
            }
        }, SIZE {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                setResult(cache.size(), message);
            }
        }, CLEAR {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                cache.clear();
            }
        }, CLEARASYNC {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                setResult(cache.clearAsync(), message);
            }
        }, QUERY {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                Query query = getQuery(configuration, cache, message);
                if (query == null) {
                    return;
                }
                setResult(query.list(), message);
            }
        }, STATS {
            @Override
            void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
                LOGGER.warn("You'll need to enable statistics to obtain meaningful data from your cache");
                setResult(((Cache) cache).getAdvancedCache().getStats(), message);
            }
        };

        private static final Operation[] OPERATIONS = values();

        void setResult(Object result, Message message) {
            message.setHeader(InfinispanConstants.RESULT, result);
        }

        Object getKey(Message message) {
            return message.getHeader(InfinispanConstants.KEY);
        }

        Object getValue(Message message) {
            return message.getHeader(InfinispanConstants.VALUE);
        }

        Object getOldValue(Message message) {
            return message.getHeader(InfinispanConstants.OLD_VALUE);
        }

        Map<? extends Object, ? extends Object>  getMap(Message message) {
            return (Map<? extends Object, ? extends Object>) message.getHeader(InfinispanConstants.MAP);
        }

        Query getQuery(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message) {
            if (InfinispanUtil.isRemote(cache)) {
                return InfinispanRemoteOperation.buildQuery(configuration, cache, message);
            } else {
                return null;
            }

        }

        boolean hasLifespan(Message message) {
            return !isHeaderEmpty(message, InfinispanConstants.LIFESPAN_TIME)
                && !isHeaderEmpty(message, InfinispanConstants.LIFESPAN_TIME_UNIT);
        }

        boolean hasMaxIdleTime(Message message) {
            return !isHeaderEmpty(message, InfinispanConstants.MAX_IDLE_TIME)
                && !isHeaderEmpty(message, InfinispanConstants.MAX_IDLE_TIME_UNIT);
        }

        abstract void execute(InfinispanConfiguration configuration, BasicCache<Object, Object> cache, Message message);

        public static Operation fromOperation(String operation) {
            int len;
            String name;

            for (int i = OPERATIONS.length - 1; i >= 0; i--) {
                name = OPERATIONS[i].name();
                len = name.length();
                if (len == operation.length() - InfinispanConstants.OPERATION_LEN) {
                    if (name.regionMatches(true, 0, operation, InfinispanConstants.OPERATION_LEN, len)) {
                        return OPERATIONS[i];
                    }
                }
            }

            throw new IllegalArgumentException("Unknown Operation for string: " + operation);
        }
    }

}
