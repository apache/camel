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
package org.apache.camel.component.atomix.client.map;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.atomix.collections.DistributedMap;
import io.atomix.resource.ReadConsistency;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Message;
import org.apache.camel.component.atomix.client.AbstractAsyncAtomixClientProducer;
import org.apache.camel.component.atomix.client.AtomixClientAction;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_ACTION;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_DEFAULT_VALUE;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_KEY;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_NAME;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_OLD_VALUE;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_READ_CONSISTENCY;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_TTL;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_VALUE;

final class AtomixClientMapProducer extends AbstractAsyncAtomixClientProducer<AtomixClientMapEndpoint> {
    private final String mapName;
    private final ConcurrentMap<String, DistributedMap<Object, Object>> maps;
    private final AtomixClientMapConfiguration configuration;

    protected AtomixClientMapProducer(AtomixClientMapEndpoint endpoint, String mapName) {
        super(endpoint);
        this.mapName = ObjectHelper.notNull(mapName, "map name");
        this.configuration = endpoint.getAtomixConfiguration();
        this.maps = new ConcurrentHashMap<>();
    }

    @Override
    protected AtomixClientAction getAction(Message message) {
        return message.getHeader(RESOURCE_ACTION, configuration::getDefaultAction, AtomixClientAction.class);
    }

    // *********************************
    // Handlers
    // *********************************

    @AsyncInvokeOnHeader(AtomixClientAction.PUT)
    boolean onPut(Message message, AsyncCallback callback) throws Exception {
        final DistributedMap<Object, Object> map = getMap(message);
        final Object key = ExchangeHelper.getMandatoryHeader(message, RESOURCE_KEY, Object.class);
        final Duration ttl = message.getHeader(RESOURCE_TTL, configuration::getTtl, Duration.class);

        if (ttl != null) {
            map.put(
                key,
                message.getMandatoryBody(),
                ttl
            ).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            map.put(
                key,
                message.getMandatoryBody()
            ).thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @AsyncInvokeOnHeader(AtomixClientAction.PUT_IF_ABSENT)
    boolean onPutIfAbsent(Message message, AsyncCallback callback) throws Exception {
        final DistributedMap<Object, Object> map = getMap(message);
        final Object key = ExchangeHelper.getMandatoryHeader(message, RESOURCE_KEY, Object.class);
        final Duration ttl = message.getHeader(RESOURCE_TTL, configuration::getTtl, Duration.class);

        if (ttl != null) {
            map.putIfAbsent(
                key,
                message.getMandatoryBody(),
                ttl
            ).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            map.putIfAbsent(
                key,
                message.getMandatoryBody()
            ).thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @AsyncInvokeOnHeader(AtomixClientAction.GET)
    boolean onGet(Message message, AsyncCallback callback) throws Exception {
        final DistributedMap<Object, Object> map = getMap(message);
        final Object key = ExchangeHelper.getMandatoryHeader(message, RESOURCE_KEY, Object.class);
        final Object defaultValue = message.getHeader(RESOURCE_DEFAULT_VALUE);
        final ReadConsistency consistency = message.getHeader(RESOURCE_READ_CONSISTENCY,  configuration::getReadConsistency, ReadConsistency.class);

        if (consistency != null) {
            if (defaultValue != null) {
                map.getOrDefault(key, defaultValue, consistency).thenAccept(
                    result -> processResult(message, callback, result)
                );
            } else {
                map.get(key, consistency).thenAccept(
                    result -> processResult(message, callback, result)
                );
            }
        } else {
            if (defaultValue != null) {
                map.getOrDefault(key, defaultValue).thenAccept(
                    result -> processResult(message, callback, result)
                );
            } else {
                map.get(key).thenAccept(
                    result -> processResult(message, callback, result)
                );
            }
        }

        return false;
    }

    @AsyncInvokeOnHeader(AtomixClientAction.CLEAR)
    boolean onClear(Message message, AsyncCallback callback) throws Exception {
        final DistributedMap<Object, Object> map = getMap(message);

        map.clear().thenAccept(
            result -> processResult(message, callback, result)
        );

        return false;
    }

    @AsyncInvokeOnHeader(AtomixClientAction.SIZE)
    boolean onSize(Message message, AsyncCallback callback) throws Exception {
        final DistributedMap<Object, Object> map = getMap(message);
        final ReadConsistency consistency = message.getHeader(RESOURCE_READ_CONSISTENCY,  configuration::getReadConsistency, ReadConsistency.class);

        if (consistency != null) {
            map.size(consistency).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            map.size().thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @AsyncInvokeOnHeader(AtomixClientAction.IS_EMPTY)
    boolean onIsEmpty(Message message, AsyncCallback callback) throws Exception {
        final DistributedMap<Object, Object> map = getMap(message);
        final ReadConsistency consistency = message.getHeader(RESOURCE_READ_CONSISTENCY,  configuration::getReadConsistency, ReadConsistency.class);

        if (consistency != null) {
            map.isEmpty(consistency).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            map.isEmpty().thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @AsyncInvokeOnHeader(AtomixClientAction.ENTRY_SET)
    boolean onEntrySet(Message message, AsyncCallback callback) throws Exception {
        final DistributedMap<Object, Object> map = getMap(message);
        final ReadConsistency consistency = message.getHeader(RESOURCE_READ_CONSISTENCY, configuration::getReadConsistency, ReadConsistency.class);

        if (consistency != null) {
            map.entrySet(consistency).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            map.entrySet().thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @AsyncInvokeOnHeader(AtomixClientAction.VALUES)
    boolean onValues(Message message, AsyncCallback callback) throws Exception {
        final DistributedMap<Object, Object> map = getMap(message);
        final ReadConsistency consistency = message.getHeader(RESOURCE_READ_CONSISTENCY, configuration::getReadConsistency, ReadConsistency.class);

        if (consistency != null) {
            map.values(consistency).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            map.values().thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @AsyncInvokeOnHeader(AtomixClientAction.CONTAINS_KEY)
    boolean onContainsKey(Message message, AsyncCallback callback) throws Exception {
        final DistributedMap<Object, Object> map = getMap(message);
        final ReadConsistency consistency = message.getHeader(RESOURCE_READ_CONSISTENCY,  configuration::getReadConsistency, ReadConsistency.class);
        final Object key = message.getHeader(RESOURCE_KEY, message::getBody, Object.class);

        ObjectHelper.notNull(key, RESOURCE_KEY);

        if (consistency != null) {
            map.containsKey(key, consistency).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            map.containsKey(key).thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @AsyncInvokeOnHeader(AtomixClientAction.CONTAINS_VALUE)
    boolean onContainsValue(Message message, AsyncCallback callback) throws Exception {
        final DistributedMap<Object, Object> map = getMap(message);
        final ReadConsistency consistency = message.getHeader(RESOURCE_READ_CONSISTENCY,  configuration::getReadConsistency, ReadConsistency.class);
        final Object value = message.getHeader(RESOURCE_VALUE, message::getBody, Object.class);

        ObjectHelper.notNull(value, RESOURCE_VALUE);

        if (consistency != null) {
            map.containsValue(value, consistency).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            map.containsValue(value).thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @AsyncInvokeOnHeader(AtomixClientAction.REMOVE)
    boolean onRemove(Message message, AsyncCallback callback) throws Exception {
        final DistributedMap<Object, Object> map = getMap(message);
        final Object key = message.getHeader(RESOURCE_KEY, message::getBody, Object.class);
        final Object value = message.getHeader(RESOURCE_VALUE, message::getBody, Object.class);

        ObjectHelper.notNull(key, RESOURCE_VALUE);

        if (value != null) {
            map.remove(key, value).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            map.remove(key).thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @AsyncInvokeOnHeader(AtomixClientAction.REPLACE)
    boolean onReplace(Message message, AsyncCallback callback) throws Exception {
        final DistributedMap<Object, Object> map = getMap(message);
        final Duration ttl = message.getHeader(RESOURCE_TTL, configuration::getTtl, Duration.class);
        final Object key = message.getHeader(RESOURCE_KEY, message::getBody, Object.class);
        final Object newValue = message.getHeader(RESOURCE_VALUE, message::getBody, Object.class);
        final Object oldValue = message.getHeader(RESOURCE_OLD_VALUE, Object.class);

        ObjectHelper.notNull(key, RESOURCE_VALUE);
        ObjectHelper.notNull(newValue, RESOURCE_VALUE);

        if (ttl != null) {
            if (oldValue != null) {
                map.replace(key, oldValue, newValue, ttl).thenAccept(
                    result -> processResult(message, callback, result)
                );
            } else {
                map.replace(key, newValue, ttl).thenAccept(
                    result -> processResult(message, callback, result)
                );
            }
        } else {
            if (oldValue != null) {
                map.replace(key, oldValue, newValue).thenAccept(
                    result -> processResult(message, callback, result)
                );
            } else {
                map.replace(key, newValue).thenAccept(
                    result -> processResult(message, callback, result)
                );
            }
        }

        return false;
    }

    // *********************************
    // Helpers
    // *********************************

    private void processResult(Message message, AsyncCallback callback, Object result) {
        if (result != null && !(result instanceof Void)) {
            message.setHeader(RESOURCE_ACTION_HAS_RESULT, true);

            String resultHeader = configuration.getResultHeader();
            if (resultHeader != null) {
                message.setHeader(resultHeader, result);
            } else {
                message.setBody(result);
            }
        } else {
            message.setHeader(RESOURCE_ACTION_HAS_RESULT, false);
        }

        callback.done(false);
    }

    private DistributedMap<Object, Object> getMap(Message message) {
        return maps.computeIfAbsent(
            message.getHeader(RESOURCE_NAME, getAtomixEndpoint().getMapName(), String.class),
            name -> {
                return getAtomixEndpoint()
                    .getAtomix()
                    .getMap(
                        name,
                        getAtomixEndpoint().getAtomixConfiguration().getConfig(),
                        getAtomixEndpoint().getAtomixConfiguration().getOptions())
                    .join();
            }
        );
    }
}
