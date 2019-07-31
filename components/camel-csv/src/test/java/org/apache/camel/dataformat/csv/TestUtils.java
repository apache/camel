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
package org.apache.camel.dataformat.csv;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class provides utility methods
 */
final class TestUtils {

    private TestUtils() {
        // Prevent instantiation
    }

    /**
     * Create a map with the given key/value pairs
     *
     * @param strings key/value pairs
     * @return Map with the given key/value pairs
     */
    static Map<String, String> asMap(String... strings) {
        if (strings.length % 2 == 1) {
            throw new IllegalArgumentException("Cannot create a map with an add number of strings");
        }

        Map<String, String> map = new LinkedHashMap<>(strings.length / 2);
        for (int i = 0; i < strings.length; i += 2) {
            map.put(strings[i], strings[i + 1]);
        }
        return map;
    }

}
