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

package org.apache.camel.resume;

import org.apache.camel.resume.cache.ResumeCache;

/**
 * Used to identify objects that can cache their resume state or data
 */
public interface Cacheable {

    /**
     * The cache fill policy can be used to determine how this cache should be filled with data.
     */
    enum FillPolicy {
        /**
         * With MAXIMIZING, entities try to maximize cache usage and fill it with as much data as possible
         */
        MAXIMIZING,

        /**
         * With MINIMIZING, entities should fill it with as little data as reasonable.
         */
        MINIMIZING,
    }

    /**
     * Adds an offset key and value to the cache
     *
     * @param  key    the key to add
     * @param  offset the offset to add
     * @return        true if added successfully (i.e.: the cache is not full) or false otherwise
     */
    boolean add(OffsetKey<?> key, Offset<?> offset);

    /**
     * Sets the cache in resume adapters and objects that cache their data
     *
     * @param cache A resume cache instance
     */
    void setCache(ResumeCache<?> cache);

    /**
     * Gets the cache in resume adapters and objects that cache their data
     *
     * @return A resume cache instance
     */
    ResumeCache<?> getCache();

    /**
     * Gets the {@Link FillPolicy} for this cache instance
     *
     * @return the fill policy set for this instance FillPolicy.MAXIMIZING
     */
    default FillPolicy getFillPolicy() {
        return FillPolicy.MAXIMIZING;
    }
}
