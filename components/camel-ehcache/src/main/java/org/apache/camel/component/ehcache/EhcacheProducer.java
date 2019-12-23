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
package org.apache.camel.component.ehcache;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Message;
import org.apache.camel.spi.InvokeOnHeader;
import org.apache.camel.support.HeaderSelectorProducer;
import org.ehcache.Cache;

public class EhcacheProducer extends HeaderSelectorProducer {
    private final EhcacheConfiguration configuration;
    private final EhcacheManager manager;
    private final Cache cache;

    public EhcacheProducer(EhcacheEndpoint endpoint, String cacheName, EhcacheConfiguration configuration) throws Exception {
        super(endpoint, EhcacheConstants.ACTION, configuration::getAction);

        this.configuration = configuration;
        this.manager = endpoint.getManager();

        Class<?> kt = null;
        if (configuration.getKeyType() != null) {
            kt = getEndpoint().getCamelContext().getClassResolver().resolveClass(configuration.getKeyType());
        }
        Class<?> vt = null;
        if (configuration.getValueType() != null) {
            vt = getEndpoint().getCamelContext().getClassResolver().resolveClass(configuration.getValueType());
        }
        this.cache = manager.getCache(cacheName, kt, vt);
    }
    
    // ****************************
    // Handlers
    // ****************************

    @InvokeOnHeader(EhcacheConstants.ACTION_CLEAR)
    public void onClear(Message message) throws Exception {
        cache.clear();

        setResult(message, true, null, null);
    }

    @InvokeOnHeader(EhcacheConstants.ACTION_PUT)
    public void onPut(Message message) throws Exception {
        cache.put(getKey(message), getValue(message, cache.getRuntimeConfiguration().getValueType()));

        setResult(message, true, null, null);
    }

    @InvokeOnHeader(EhcacheConstants.ACTION_PUT_ALL)
    public void onPutAll(Message message) throws Exception {
        cache.putAll((Map)getValue(message, Map.class));

        setResult(message, true, null, null);
    }

    @InvokeOnHeader(EhcacheConstants.ACTION_PUT_IF_ABSENT)
    public void onPutIfAbsent(Message message) throws Exception {
        Object oldValue = cache.putIfAbsent(getKey(message), getValue(message, cache.getRuntimeConfiguration().getValueType()));

        setResult(message, true, null, oldValue);
    }

    @InvokeOnHeader(EhcacheConstants.ACTION_GET)
    public void onGet(Message message) throws Exception {
        Object result = cache.get(getKey(message));

        setResult(message, true, result, null);
    }

    @InvokeOnHeader(EhcacheConstants.ACTION_GET_ALL)
    public void onGetAll(Message message) throws Exception {
        Object result = cache.getAll(
            message.getHeader(EhcacheConstants.KEYS, Collections::emptySet, Set.class)
        );

        setResult(message, true, result, null);
    }

    @InvokeOnHeader(EhcacheConstants.ACTION_REMOVE)
    public void onRemove(Message message) throws Exception {
        boolean success = true;
        Object valueToReplace = message.getHeader(EhcacheConstants.OLD_VALUE);
        if (valueToReplace == null) {
            cache.remove(getKey(message));
        } else {
            success = cache.remove(getKey(message), valueToReplace);
        }

        setResult(message, success, null, null);
    }

    @InvokeOnHeader(EhcacheConstants.ACTION_REMOVE_ALL)
    public void onRemoveAll(Message message) throws Exception {
        cache.removeAll(
            message.getHeader(EhcacheConstants.KEYS, Collections::emptySet, Set.class)
        );

        setResult(message, true, null, null);
    }

    @InvokeOnHeader(EhcacheConstants.ACTION_REPLACE)
    public void onReplace(Message message) throws Exception {
        boolean success = true;
        Object oldValue = null;
        Object value = getValue(message, cache.getRuntimeConfiguration().getValueType());
        Object valueToReplace = message.getHeader(EhcacheConstants.OLD_VALUE);

        if (valueToReplace == null) {
            oldValue = cache.replace(getKey(message), value);
        } else {
            success = cache.replace(getKey(message), valueToReplace, value);
        }

        setResult(message, success, null, oldValue);
    }

    // ****************************
    // Helpers
    // ****************************

    private Object getKey(final Message message) throws Exception {
        Object value;
        if (configuration.getKeyType() != null) {
            Class<?> clazz = getEndpoint().getCamelContext().getClassResolver().resolveClass(configuration.getKeyType());
            value = message.getHeader(EhcacheConstants.KEY, clazz);
        } else {
            value = message.getHeader(EhcacheConstants.KEY);
        }
        if (value == null) {
            value = configuration.getKey();
        }

        if (value == null) {
            throw new CamelExchangeException(
                "No value provided in header or as default value (" + EhcacheConstants.KEY + ")",
                message.getExchange()
            );
        }

        return value;
    }

    @SuppressWarnings("unchecked")
    private Object getValue(final Message message, final Object type)  throws Exception {
        Object value = message.getHeader(EhcacheConstants.VALUE);
        if (value == null) {
            if (type instanceof String) {
                Class<?> clazz = getEndpoint().getCamelContext().getClassResolver().resolveClass((String) type);
                value = message.getBody(clazz);
            } else if (type instanceof Class) {
                value = message.getBody((Class) type);
            } else {
                value = message.getBody();
            }
        }

        if (value == null) {
            throw new CamelExchangeException(
                "No value provided in header or body (" + EhcacheConstants.VALUE + ")",
                message.getExchange()
            );
        }

        return value;
    }

    private void setResult(Message message, boolean success, Object result, Object oldValue) {
        message.setHeader(EhcacheConstants.ACTION_SUCCEEDED, success);
        message.setHeader(EhcacheConstants.ACTION_HAS_RESULT, oldValue != null || result != null);

        if (oldValue != null) {
            message.setHeader(EhcacheConstants.OLD_VALUE, oldValue);
        }
        if (result != null) {
            message.setBody(result);
        }
    }
}
