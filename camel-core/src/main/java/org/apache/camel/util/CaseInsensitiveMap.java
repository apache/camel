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
package org.apache.camel.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A map that uses case insensitive keys, but preserves the original keys in the keySet.
 * <p/>
 * This map allows you to do lookup using case insensitive keys so you can retrieve the value without worrying about
 * whether some transport protocol affects the keys such as Http and Mail protocols can do.
 * <p/>
 * When copying from this map to a regular Map such as {@link java.util.HashMap} then the original keys are
 * copied over and you get the old behavior back using a regular Map with case sensitive keys.
 * <p/>
 * This map is <b>not</b> designed to be thread safe as concurrent access to it is not supposed to be performed
 * by the Camel routing engine.
 *
 * @version $Revision$
 */
public class CaseInsensitiveMap extends HashMap<String, Object> {
    private static final long serialVersionUID = -8538318195477618308L;

    // holds a map of lower case key -> original key
    private Map<String, String> originalKeys;
    // holds a snapshot view of current entry set
    private transient Set<Map.Entry<String, Object>> entrySetView;

    public CaseInsensitiveMap() {
        super();
        originalKeys = new HashMap<String, String>();
    }

    public CaseInsensitiveMap(Map<? extends String, ?> map) {
        this();
        putAll(map);
    }

    public CaseInsensitiveMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
        originalKeys = new HashMap<String, String>(initialCapacity);
    }

    public CaseInsensitiveMap(int initialCapacity) {
        super(initialCapacity);
        originalKeys = new HashMap<String, String>();
    }

    @Override
    public Object get(Object key) {
        String s = key.toString().toLowerCase();
        Object answer = super.get(s);
        if (answer == null) {
            // fallback to lookup by original key
            String originalKey = originalKeys.get(s);
            answer = super.get(originalKey);
        }
        return answer;
    }

    @Override
    public synchronized Object put(String key, Object value) {
        // invalidate views as we mutate
        entrySetView = null;
        String s = key.toLowerCase();
        originalKeys.put(s, key);
        return super.put(s, value);
    }

    @Override
    public synchronized void putAll(Map<? extends String, ?> map) {
        if (map != null && !map.isEmpty()) {
            for (Map.Entry<? extends String, ?> entry : map.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public synchronized Object remove(Object key) {
        if (key == null) {
            return null;
        }

        // invalidate views as we mutate
        entrySetView = null;
        String s = key.toString().toLowerCase();
        originalKeys.remove(s);
        return super.remove(s);
    }

    @Override
    public synchronized void clear() {
        // invalidate views as we mutate
        entrySetView = null;
        originalKeys.clear();
        super.clear();
    }

    @Override
    public boolean containsKey(Object key) {
        if (key == null) {
            return false;
        }

        String s = key.toString().toLowerCase();
        return super.containsKey(s);
    }

    @Override
    public synchronized Set<Map.Entry<String, Object>> entrySet() {
        if (entrySetView == null) {
            // build the key set using the original keys so we retain their case
            // when for example we copy values to another map
            entrySetView = new HashSet<Map.Entry<String, Object>>(this.size());
            for (final Map.Entry<String, Object> entry : super.entrySet()) {
                Map.Entry<String, Object> view = new Map.Entry<String, Object>() {
                    public String getKey() {
                        String s = entry.getKey();
                        // use the original key so we can preserve it
                        return originalKeys.get(s);
                    }

                    public Object getValue() {
                        return entry.getValue();
                    }

                    public Object setValue(Object o) {
                        return entry.setValue(o);
                    }
                };
                entrySetView.add(view);
            }
        }

        return entrySetView;
    }
}