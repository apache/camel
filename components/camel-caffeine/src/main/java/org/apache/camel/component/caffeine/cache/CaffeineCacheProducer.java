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
package org.apache.camel.component.caffeine.cache;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.github.benmanes.caffeine.cache.Cache;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Message;
import org.apache.camel.component.caffeine.CaffeineConfiguration;
import org.apache.camel.component.caffeine.CaffeineConstants;
import org.apache.camel.spi.InvokeOnHeader;
import org.apache.camel.support.HeaderSelectorProducer;

public class CaffeineCacheProducer extends HeaderSelectorProducer {
    private final CaffeineConfiguration configuration;
    private final Cache cache;

    public CaffeineCacheProducer(CaffeineCacheEndpoint endpoint, String cacheName, CaffeineConfiguration configuration, Cache cache) throws Exception {
        super(endpoint, CaffeineConstants.ACTION, configuration::getAction);
        this.configuration = configuration;
        this.cache = cache;
    }

    // ****************************
    // Handlers
    // ****************************

    @InvokeOnHeader(CaffeineConstants.ACTION_CLEANUP)
    public void onCleanUp(Message message) throws Exception {
        cache.cleanUp();

        setResult(message, true, null, null);
    }

    @InvokeOnHeader(CaffeineConstants.ACTION_PUT)
    public void onPut(Message message) throws Exception {
        cache.put(getKey(message), getValue(message, configuration.getValueType()));

        setResult(message, true, null, null);
    }

    @InvokeOnHeader(CaffeineConstants.ACTION_PUT_ALL)
    public void onPutAll(Message message) throws Exception {
        cache.putAll((Map)getValue(message, Map.class.getName()));

        setResult(message, true, null, null);
    }

    @InvokeOnHeader(CaffeineConstants.ACTION_GET)
    public void onGet(Message message) throws Exception {
        Object result = cache.getIfPresent(getKey(message));

        setResult(message, true, result, null);
    }

    @InvokeOnHeader(CaffeineConstants.ACTION_GET_ALL)
    public void onGetAll(Message message) throws Exception {
        Object result = cache.getAllPresent(message.getHeader(CaffeineConstants.KEYS, Collections::emptySet, Set.class));

        setResult(message, true, result, null);
    }

    @InvokeOnHeader(CaffeineConstants.ACTION_INVALIDATE)
    public void onInvalidate(Message message) throws Exception {
        cache.invalidate(getKey(message));

        setResult(message, true, null, null);
    }

    @InvokeOnHeader(CaffeineConstants.ACTION_INVALIDATE_ALL)
    public void onInvalidateAll(Message message) throws Exception {
        cache.invalidateAll(message.getHeader(CaffeineConstants.KEYS, Collections::emptySet, Set.class));

        setResult(message, true, null, null);
    }

    // ****************************
    // Helpers
    // ****************************

    private Object getKey(final Message message) throws Exception {
        Object value;
        if (configuration.getKeyType() != null) {
            Class<?> clazz = getEndpoint().getCamelContext().getClassResolver().resolveClass(configuration.getKeyType());
            value = message.getHeader(CaffeineConstants.KEY, clazz);
        } else {
            value = message.getHeader(CaffeineConstants.KEY);
        }
        if (value == null) {
            value = configuration.getKey();
        }

        if (value == null) {
            throw new CamelExchangeException("No value provided in header or as default value (" + CaffeineConstants.KEY + ")", message.getExchange());
        }

        return value;
    }

    private Object getValue(final Message message, final String type) throws Exception {
        Object value = message.getHeader(CaffeineConstants.VALUE);
        if (value == null) {
            if (type != null) {
                Class<?> clazz = getEndpoint().getCamelContext().getClassResolver().resolveClass(type);
                value = message.getBody(clazz);
            } else {
                value = message.getBody();
            }
        }

        if (value == null) {
            throw new CamelExchangeException("No value provided in header or body (" + CaffeineConstants.VALUE + ")", message.getExchange());
        }

        return value;
    }

    private void setResult(Message message, boolean success, Object result, Object oldValue) {
        message.setHeader(CaffeineConstants.ACTION_SUCCEEDED, success);
        message.setHeader(CaffeineConstants.ACTION_HAS_RESULT, oldValue != null || result != null);

        if (oldValue != null) {
            message.setHeader(CaffeineConstants.OLD_VALUE, oldValue);
        }
        if (result != null) {
            message.setBody(result);
        }
    }
}
