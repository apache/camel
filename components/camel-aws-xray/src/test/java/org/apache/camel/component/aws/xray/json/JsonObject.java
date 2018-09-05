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
package org.apache.camel.component.aws.xray.json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class JsonObject implements JsonStructure {

    private Map<String, Object> data = new LinkedHashMap<>();

    void addElement(String key, Object value) {
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public Set<String> getKeys() {
        return data.keySet();
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    public String getString(String key) {
        if (data.containsKey(key) && null != data.get(key)) {
            return data.get(key).toString();
        }
        return null;
    }

    public Double getDouble(String key) {
        if (data.containsKey(key) && null != data.get(key)) {
            Object value = data.get(key);
            if (value instanceof String) {
                return Double.valueOf((String) value);
            }
            return (Double) value;
        }
        return 0D;
    }

    public Long getLong(String key) {
        if (data.containsKey(key) && null != data.get(key)) {
            Object value = data.get(key);
            if (value instanceof String) {
                return Long.valueOf((String) value);
            }
            return (Long) value;
        }
        return 0L;
    }

    public Integer getInteger(String key) {
        if (data.containsKey(key) && null != data.get(key)) {
            Object value = data.get(key);
            if (value instanceof String) {
                return Integer.valueOf((String) value);
            }
            return (Integer) value;
        }
        return 0;
    }

    public Boolean getBoolean(String key) {
        if (data.containsKey(key) && null != data.get(key)) {
            Object value = data.get(key);
            if (value instanceof String) {
                return Boolean.valueOf((String) value);
            }
            return (Boolean) value;
        }
        return null;
    }
}
