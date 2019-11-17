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
package org.apache.camel.dataformat.univocity;

import java.util.LinkedHashMap;
import java.util.Map;

import static java.lang.System.lineSeparator;

/**
 * This class provides utility methods for the unit tests
 */
final class UniVocityTestHelper {

    private UniVocityTestHelper() {
        // Helper class
    }

    /**
     * Creates a Map with the given key values
     *
     * @param keyValues the key values
     * @return Map with the given key values
     */
    public static Map<String, String> asMap(String... keyValues) {
        if (keyValues == null || keyValues.length % 2 == 1) {
            throw new IllegalArgumentException("You must specify key values with an even number.");
        }

        Map<String, String> result = new LinkedHashMap<>(keyValues.length / 2);
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(keyValues[i], keyValues[i + 1]);
        }
        return result;
    }

    /**
     * Joins the given lines with the platform new line.
     *
     * @param lines lines to join
     * @return joined lines with the platform new line
     */
    public static String join(String... lines) {
        if (lines == null || lines.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append(lineSeparator());
        }
        return sb.toString();
    }
}
