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
package org.apache.camel.opentracing.propagation;

import java.util.Iterator;
import java.util.Map;

import io.opentracing.propagation.TextMap;

public final class CamelHeadersInjectAdapter implements TextMap {
    private final Map<String, Object> map;

    public CamelHeadersInjectAdapter(final Map<String, Object> map) {
        this.map = map;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        throw new UnsupportedOperationException("CamelHeadersInjectAdapter should only be used with Tracer.inject()");
    }

    @Override
    public void put(String key, String value) {
        // Assume any header property that begins with 'Camel' is for internal use
        if (!key.startsWith("Camel")) {
            this.map.put(key, value);
        }
    }
}