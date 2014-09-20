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
package org.apache.camel.dataformat.csv;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This {@code CsvLineConverters} class provides common implementations of the {@code CsvLineConverter} interface.
 */
public final class CsvLineConverters {

    private CsvLineConverters() {
        // Prevent instantiation
    }
    /**
     * Provides an implementation of {@code CsvLineConverter} that converts a line into a {@code List}.
     *
     * @return List-based {@code CsvLineConverter} implementation
     */
    public static CsvLineConverter<List<String>> getListConverter() {
        return ListLineConverter.SINGLETON;
    }

    /**
     * Provides an implementation of {@code CsvLineConverter} that converts a line into a {@code Map}.
     * <p/>
     * It requires to have unique {@code headers} values as well as the same number of item in each line.
     *
     * @param headers Headers of the CSV file
     * @return Map-based {@code CsvLineConverter} implementation
     */
    public static CsvLineConverter<Map<String, String>> getMapLineConverter(String[] headers) {
        return new MapLineConverter(headers);
    }

    private static final class ListLineConverter implements CsvLineConverter<List<String>> {
        public static final ListLineConverter SINGLETON = new ListLineConverter();

        @Override
        public List<String> convertLine(String[] line) {
            return Arrays.asList(line);
        }
    }

    private static final class MapLineConverter implements CsvLineConverter<Map<String, String>> {
        private final String[] headers;

        private MapLineConverter(String[] headers) {
            this.headers = checkHeaders(headers);
        }

        @Override
        public Map<String, String> convertLine(String[] line) {
            if (line.length != headers.length) {
                throw new IllegalStateException("This line does not have the same number of items than the header");
            }

            Map<String, String> result = new HashMap<String, String>(line.length);
            for (int i = 0; i < line.length; i++) {
                result.put(headers[i], line[i]);
            }
            return result;
        }

        private static String[] checkHeaders(String[] headers) {
            // Check that we have headers
            if (headers == null || headers.length == 0) {
                throw new IllegalArgumentException("Missing headers for the CSV parsing");
            }

            // Check that there is no duplicates
            Set<String> headerSet = new HashSet<String>(headers.length);
            Collections.addAll(headerSet, headers);
            if (headerSet.size() != headers.length) {
                throw new IllegalArgumentException("There are duplicate headers");
            }

            return headers;
        }
    }

}
