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
package org.apache.camel.component.salesforce.api.dto;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.thoughtworks.xstream.converters.reflection.FieldKey;
import com.thoughtworks.xstream.converters.reflection.FieldKeySorter;

public final class AnnotationFieldKeySorter implements FieldKeySorter {

    private static final class AnnotationFieldOrderComparator implements Comparator<FieldKey> {
        private final SortedMap<String, Integer> order = new TreeMap<>();

        private AnnotationFieldOrderComparator(final String[] orderedFields, Field[] fields) {
            int i = 0;
            for (; i < orderedFields.length; i++) {
                order.put(orderedFields[i], i);
            }
            for (int j = 0; j < fields.length; j++) {
                order.putIfAbsent(fields[j].getName(), i + j);
            }
        }

        @Override
        public int compare(final FieldKey k1, final FieldKey k2) {
            final String field1 = k1.getFieldName();
            final String field2 = k2.getFieldName();

            return order.get(field1).compareTo(order.get(field2));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map sort(final Class type, final Map keyedByFieldKey) {
        final Class<?> clazz = type;

        final XStreamFieldOrder fieldOrderAnnotation = clazz.getAnnotation(XStreamFieldOrder.class);
        if (fieldOrderAnnotation == null) {
            return keyedByFieldKey;
        }

        final String[] fieldOrder = fieldOrderAnnotation.value();
        final TreeMap<FieldKey, Field> sorted = new TreeMap<>(
            new AnnotationFieldOrderComparator(fieldOrder, type.getDeclaredFields()));
        sorted.putAll(keyedByFieldKey);

        return sorted;
    }
}
