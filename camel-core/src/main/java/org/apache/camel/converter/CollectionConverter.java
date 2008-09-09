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
package org.apache.camel.converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.Converter;

/**
 * Some core java.util Collection based
 * <a href="http://activemq.apache.org/camel/type-converter.html">Type Converters</a>
 *
 * @version $Revision$
 */
@Converter
public final class CollectionConverter {

    /**
     * Utility classes should not have a public constructor.
     */
    private CollectionConverter() {
    }

    /**
     * Converts a collection to an array
     */
    @Converter
    public static Object[] toArray(Collection value) {
        if (value == null) {
            return null;
        }
        return value.toArray();
    }

    /**
     * Converts an array to a collection
     */
    @Converter
    public static List toList(Object[] array) {
        return Arrays.asList(array);
    }

    /**
     * Converts a collection to a List if it is not already
     */
    @Converter
    public static List toList(Collection collection) {
        return new ArrayList(collection);
    }
    
    /**
     * Converts an {@link Iterator} to a {@link ArrayList}
     */
    @Converter
    public static ArrayList toArrayList(Iterator it) {
        ArrayList list = new ArrayList();
        while (it.hasNext()) {
            list.add(it.next());
        }
        return list;
    }

    @Converter
    public static Set toSet(Object[] array) {
        Set answer = new HashSet();
        answer.addAll(Arrays.asList(array));
        return answer;
    }

    @Converter
    public static Set toSet(Collection collection) {
        return new HashSet(collection);
    }

    @Converter
    public static Set toSet(Map map) {
        return map.entrySet();
    }

    @Converter
    public static Properties toProperties(Map map) {
        Properties answer = new Properties();
        answer.putAll(map);
        return answer;
    }

    @Converter
    public static Hashtable toHashtable(Map map) {
        return new Hashtable(map);
    }

    @Converter
    public static HashMap toHashMap(Map map) {
        return new HashMap(map);
    }

    /**
     * Converts an {@link Iterable} into a {@link List} 
     */
    @Converter
    public static List toList(Iterable iterable) {
        if (iterable instanceof List) {
            return (List) iterable;
        }
        List result = new LinkedList();
        for (Object value : iterable) {
            result.add(value);
        }
        return result;
    }
}
