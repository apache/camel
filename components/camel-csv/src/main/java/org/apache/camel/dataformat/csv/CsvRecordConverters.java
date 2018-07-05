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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVRecord;

/**
 * This class defines common {@link CsvRecordConverter} implementations.
 *
 * @see CsvRecordConverter
 */
final class CsvRecordConverters {
    private CsvRecordConverters() {
        // Prevent instantiation
    }

    /**
     * Returns a converter that transforms the CSV record into a list.
     *
     * @return converter that transforms the CSV record into a list
     */
    public static CsvRecordConverter<List<String>> listConverter() {
        return ListCsvRecordConverter.SINGLETON;
    }

    private static final class ListCsvRecordConverter implements CsvRecordConverter<List<String>> {
        private static final ListCsvRecordConverter SINGLETON = new ListCsvRecordConverter();

        @Override
        public List<String> convertRecord(CSVRecord record) {
            List<String> answer = new ArrayList<>(record.size());
            for (int i = 0; i < record.size(); i++) {
                answer.add(record.get(i));
            }
            return answer;
        }
    }

    /**
     * Returns a converter that transforms the CSV record into a map.
     *
     * @return converter that transforms the CSV record into a map
     */
    public static CsvRecordConverter<Map<String, String>> mapConverter() {
        return MapCsvRecordConverter.SINGLETON;
    }

    private static class MapCsvRecordConverter implements CsvRecordConverter<Map<String, String>> {
        private static final MapCsvRecordConverter SINGLETON = new MapCsvRecordConverter();

        @Override
        public Map<String, String> convertRecord(CSVRecord record) {
            return record.toMap();
        }
    }

    /**
     * Returns a converter that transforms the CSV record into an ordered map.
     *
     * @return converter that transforms the CSV record into an ordered map
     */
    public static CsvRecordConverter<Map<String, String>> orderedMapConverter() {
        return OrderedMapCsvRecordConverter.SINGLETON;
    }

    private static class OrderedMapCsvRecordConverter implements CsvRecordConverter<Map<String, String>> {
        private static final OrderedMapCsvRecordConverter SINGLETON = new OrderedMapCsvRecordConverter();

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, String> convertRecord(CSVRecord record) {
            Map<String, String> answer = new LinkedHashMap<>();

            // use reflection because CSVRecord does not return maps ordered
            try {
                Field field = record.getClass().getDeclaredField("mapping");
                field.setAccessible(true);
                Map<String, Integer> mapping = (Map<String, Integer>) field.get(record);
                if (mapping != null) {
                    for (Object o : mapping.entrySet()) {
                        Map.Entry<String, Integer> entry = (Map.Entry) o;
                        int col = entry.getValue();
                        answer.put(entry.getKey(), record.get(col));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                // ignore
            }
            return answer;
        }
    }
}
