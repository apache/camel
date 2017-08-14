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
package org.apache.camel.impl.converter;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.util.LRUCacheFactory;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses the {@link java.beans.PropertyEditor} conversion system to convert Objects to
 * and from String values.
 *
 * @deprecated should be removed as it can cause side-effects when using 3rd party property editors
 *
 * @version 
 */
@Deprecated
public class PropertyEditorTypeConverter implements TypeConverter {

    private static final Logger LOG = LoggerFactory.getLogger(PropertyEditorTypeConverter.class);
    // use a soft bound cache to avoid using too much memory in case a lot of different classes
    // is being converted to string
    @SuppressWarnings("unchecked")
    private final Map<Class<?>, Class<?>> misses = LRUCacheFactory.newLRUSoftCache(1000);
    // we don't anticipate so many property editors so we have unbounded map
    private final Map<Class<?>, PropertyEditor> cache = new HashMap<Class<?>, PropertyEditor>();

    public void clear() {
        cache.clear();
        misses.clear();
    }

    @Override
    public boolean allowNull() {
        return false;
    }

    @Override
    public <T> T convertTo(Class<T> type, Object value) {
        // We can't convert null values since we can't figure out a property
        // editor for it.
        if (value == null) {
            return null;
        }

        if (value.getClass() == String.class) {
            // No conversion needed.
            if (type == String.class) {
                return ObjectHelper.cast(type, value);
            }

            Class<?> key = type;
            PropertyEditor editor = lookupEditor(key);
            if (editor != null) {
                // we are essentially not thread safe as we use 2 calls to convert
                editor.setAsText(value.toString());
                return ObjectHelper.cast(type, editor.getValue());
            }
        } else if (type == String.class) {
            Class<?> key = value.getClass();
            PropertyEditor editor = lookupEditor(key);
            if (editor != null) {
                // we are essentially not thread safe as we use 2 calls to convert
                editor.setValue(value);
                return ObjectHelper.cast(type, editor.getAsText());
            }
        }

        return null;
    }

    private PropertyEditor lookupEditor(Class<?> type) {
        // check misses first
        if (misses.containsKey(type)) {
            LOG.trace("No previously found property editor for type: {}", type);
            return null;
        }

        synchronized (cache) {
            // not a miss then try to lookup the editor
            PropertyEditor editor = cache.get(type);
            if (editor == null) {
                // findEditor is synchronized and very slow so we want to only lookup once for a given key
                // and then we use our own local cache for faster lookup
                editor = PropertyEditorManager.findEditor(type);

                // either we found an editor, or if not then register it as a miss
                if (editor != null) {
                    LOG.trace("Found property editor for type: {} -> {}", type, editor);
                    cache.put(type, editor);
                } else {
                    LOG.trace("Cannot find property editor for type: {}", type);
                    misses.put(type, type);
                }
            }
            return editor;
        }
    }

    @Override
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
        return convertTo(type, value);
    }

    @Override
    public <T> T mandatoryConvertTo(Class<T> type, Object value) {
        return convertTo(type, value);
    }

    @Override
    public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value) {
        return convertTo(type, value);
    }

    @Override
    public <T> T tryConvertTo(Class<T> type, Exchange exchange, Object value) {
        try {
            return convertTo(type, exchange, value);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public <T> T tryConvertTo(Class<T> type, Object value) {
        try {
            return convertTo(type, null, value);
        } catch (Exception e) {
            return null;
        }
    }
}
