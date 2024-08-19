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
package org.apache.camel.impl.converter;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.support.TypeConverterSupport;

/**
 * A type converter which is used to convert to and from array types particularly for derived types of array component
 * types and dealing with primitive array types.
 */
public class ArrayTypeConverter extends TypeConverterSupport {

    @Override
    @SuppressWarnings("unchecked")
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
        if (type.isArray()) {
            if (value instanceof Collection collection) {
                Object array = Array.newInstance(type.getComponentType(), collection.size());
                if (array instanceof Object[]) {
                    collection.toArray((Object[]) array);
                } else {
                    int index = 0;
                    for (Object element : collection) {
                        Array.set(array, index++, element);
                    }
                }
                return (T) array;
            } else if (value != null && value.getClass().isArray()) {
                int size = Array.getLength(value);
                Object answer = Array.newInstance(type.getComponentType(), size);
                for (int i = 0; i < size; i++) {
                    Array.set(answer, i, Array.get(value, i));
                }
                return (T) answer;
            }
        } else if (Collection.class.isAssignableFrom(type)) {
            if (value != null) {
                if (value instanceof Object[]) {
                    return (T) Arrays.asList((Object[]) value);
                } else if (value.getClass().isArray()) {
                    int size = Array.getLength(value);
                    List<Object> answer = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        answer.add(Array.get(value, i));
                    }
                    return (T) answer;
                }
            }
        }
        return null;
    }

}
