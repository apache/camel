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
package org.apache.camel.util;

/**
 * Represents a thread safe map of values which timeout after a period of
 * inactivity.
 *
 * @version $Revision$
 */
public interface TimeoutMap extends Runnable {

    /**
     * Looks up the value in the map by the given key.
     *
     * @param key the key of the value to search for
     * @return the value for the given key or null if it is not present (or has timed out)
     */
    Object get(Object key);

    /**
     * Returns a copy of the keys in the map
     */
    Object[] getKeys();

    /**
     * Returns the size of the map
     */
    int size();

    /**
     * Adds the key value pair into the map such that some time after the given
     * timeout the entry will be evicted
     */
    void put(Object key, Object value, long timeoutMillis);

    /**
     * Removes the object with the given key
     *
     * @param key  key for the object to remove
     */
    void remove(Object key);

    /**
     * Purges any old entries from the map
     */
    void purge();
}
