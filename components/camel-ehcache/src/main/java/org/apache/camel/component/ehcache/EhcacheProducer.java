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
package org.apache.camel.component.ehcache;

import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.impl.DefaultProducer;
import org.ehcache.Cache;

public class EhcacheProducer extends DefaultProducer {
    private final EhcacheConfiguration configuration;
    private final EhcacheManager manager;
    private final Cache cache;

    public EhcacheProducer(EhcacheEndpoint endpoint, EhcacheConfiguration configuration) throws Exception {
        super(endpoint);

        this.configuration = configuration;
        this.manager = endpoint.getManager();
        this.cache = manager.getCache();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final Message message = exchange.getIn();
        final String action = exchange.getIn().getHeader(EhcacheConstants.ACTION, configuration.getAction(), String.class);

        if (action == null) {
            throw new NoSuchHeaderException(exchange, EhcacheConstants.ACTION, String.class);
        }

        switch (action) {
        case EhcacheConstants.ACTION_CLEAR:
            onClear(message);
            break;
        case EhcacheConstants.ACTION_PUT:
            onPut(message);
            break;
        case EhcacheConstants.ACTION_PUT_ALL:
            onPutAll(message);
            break;
        case EhcacheConstants.ACTION_PUT_IF_ABSENT:
            onPutIfAbsent(message);
            break;
        case EhcacheConstants.ACTION_GET:
            onGet(message);
            break;
        case EhcacheConstants.ACTION_GET_ALL:
            onGetAll(message);
            break;
        case EhcacheConstants.ACTION_REMOVE:
            onRemove(message);
            break;
        case EhcacheConstants.ACTION_REMOVE_ALL:
            onRemoveAll(message);
            break;
        case EhcacheConstants.ACTION_REPLACE:
            onReplace(message);
            break;
        default:
            throw new IllegalStateException("Unsupported operation " + action);
        }
    }
    
    // ****************************
    // Handlers
    // ****************************

    private void onClear(Message message) throws Exception {
        cache.clear();

        setResult(message, true, null, null);
    }

    private void onPut(Message message) throws Exception {
        cache.put(getKey(message), getValue(message, configuration.getValueType()));

        setResult(message, true, null, null);
    }

    private void onPutAll(Message message) throws Exception {
        cache.putAll((Map)getValue(message, Map.class));

        setResult(message, true, null, null);
    }

    private void onPutIfAbsent(Message message) throws Exception {
        Object oldValue = cache.putIfAbsent(getKey(message), getValue(message, configuration.getValueType()));

        setResult(message, true, null, oldValue);
    }

    private void onGet(Message message) throws Exception {
        Object result = cache.get(getKey(message));

        setResult(message, true, result, null);
    }

    private void onGetAll(Message message) throws Exception {
        Object result = cache.getAll(message.getHeader(EhcacheConstants.KEYS, Set.class));

        setResult(message, true, result, null);
    }

    private void onRemove(Message message) throws Exception {
        boolean success = true;
        Object valueToReplace = message.getHeader(EhcacheConstants.OLD_VALUE);
        if (valueToReplace == null) {
            cache.remove(getKey(message));
        } else {
            success = cache.remove(getKey(message), valueToReplace);
        }

        setResult(message, success, null, null);
    }

    private void onRemoveAll(Message message) throws Exception {
        cache.removeAll(message.getHeader(EhcacheConstants.KEYS, Set.class));

        setResult(message, true, null, null);
    }

    private void onReplace(Message message) throws Exception {
        boolean success = true;
        Object oldValue = null;
        Object value = getValue(message, configuration.getValueType());
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
        Object value = message.getHeader(EhcacheConstants.KEY, configuration.getKeyType());
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

    private Object getValue(final Message message, final Class<?> type)  throws Exception {
        Object value = message.getHeader(EhcacheConstants.VALUE, type);
        if (value == null) {
            value = message.getBody(type);
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
