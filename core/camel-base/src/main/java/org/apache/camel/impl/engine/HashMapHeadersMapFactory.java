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
package org.apache.camel.impl.engine;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.spi.HeadersMapFactory;

/**
 * HashMap {@link HeadersMapFactory} which uses a plain {@link java.util.HashMap}.
 * Important: The map is case sensitive which means headers such as <tt>content-type</tt> and <tt>Content-Type</tt> are
 * two different keys which can be a problem for some protocols such as HTTP based.
 * Therefore use this implementation with care.
 */
public class HashMapHeadersMapFactory implements HeadersMapFactory {

    @Override
    public Map<String, Object> newMap() {
        return new HashMap<>();
    }

    @Override
    public Map<String, Object> newMap(Map<String, Object> map) {
        return new HashMap<>(map);
    }

    @Override
    public boolean isInstanceOf(Map<String, Object> map) {
        return map instanceof HashMap;
    }

    @Override
    public boolean isCaseInsensitive() {
        return false;
    }
}
