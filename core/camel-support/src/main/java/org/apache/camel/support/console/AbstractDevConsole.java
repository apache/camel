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
package org.apache.camel.support.console;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.TypeConverter;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Base implementation for {@link DevConsole}.
 */
public abstract class AbstractDevConsole extends ServiceSupport implements DevConsole, CamelContextAware {

    private CamelContext camelContext;
    private final Lock lock;
    private final String group;
    private final String id;
    private final String displayName;
    private final String description;

    protected AbstractDevConsole(String group, String id, String displayName, String description) {
        this.lock = new ReentrantLock();
        this.group = group;
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public boolean supportMediaType(MediaType mediaType) {
        return true;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractDevConsole)) {
            return false;
        }

        AbstractDevConsole that = (AbstractDevConsole) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public Object call(MediaType mediaType, Map<String, Object> options) {
        lock.lock();
        try {
            if (mediaType == MediaType.JSON) {
                return doCallJson(options);
            } else {
                return doCallText(options);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Extracts an option value as String from the options map.
     */
    protected String optionString(Map<String, Object> options, String key) {
        Object val = options.get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof String s) {
            return s;
        }
        TypeConverter tc = getCamelContext().getTypeConverter();
        return tc.tryConvertTo(String.class, val);
    }

    /**
     * Extracts an option value as int from the options map.
     */
    protected int optionInt(Map<String, Object> options, String key, int defaultValue) {
        Object val = options.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Integer i) {
            return i;
        }
        TypeConverter tc = getCamelContext().getTypeConverter();
        Integer result = tc.tryConvertTo(Integer.class, val);
        return result != null ? result : defaultValue;
    }

    /**
     * Extracts an option value as boolean from the options map.
     */
    protected boolean optionBoolean(Map<String, Object> options, String key, boolean defaultValue) {
        Object val = options.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        TypeConverter tc = getCamelContext().getTypeConverter();
        Boolean result = tc.tryConvertTo(Boolean.class, val);
        return result != null ? result : defaultValue;
    }

    /**
     * Extracts an option value as long from the options map.
     */
    protected long optionLong(Map<String, Object> options, String key, long defaultValue) {
        Object val = options.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Long l) {
            return l;
        }
        TypeConverter tc = getCamelContext().getTypeConverter();
        Long result = tc.tryConvertTo(Long.class, val);
        return result != null ? result : defaultValue;
    }

    /**
     * Invokes and gets the output from this console in json format.
     *
     * The returned object can for example be an <tt>org.apache.camel.util.json.JsonObject</tt> from camel-util-json to
     * represent JSon data.
     *
     * @see DevConsole#call(MediaType, Map)
     */
    protected abstract Map<String, Object> doCallJson(Map<String, Object> options);

    /**
     * Invokes and gets the output from this console in text format.
     *
     * @see DevConsole#call(MediaType, Map)
     */
    protected abstract String doCallText(Map<String, Object> options);

}
