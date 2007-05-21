/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A number of helper methods for working with collections
 *
 * @version $Revision: 1.1 $
 */
public class CollectionHelper {
    /**
     * Sets the value of the entry in the map for the given key, though if the map already contains a value for the
     * given key then the value is appended to a list of values.
     *
     * @param map   the map to add the entry to
     * @param key   the key in the map
     * @param value the value to put in the map
     */
    public static void appendValue(Map map, Object key, Object value) {

        Object oldValue = map.get(key);
        if (oldValue != null) {
            List list;
            if (oldValue instanceof List) {
                list = (List) oldValue;
            }
            else {
                list = new ArrayList();
                list.add(oldValue);
            }
            list.add(value);
        }
        else {
            map.put(key, value);
        }
    }
}
