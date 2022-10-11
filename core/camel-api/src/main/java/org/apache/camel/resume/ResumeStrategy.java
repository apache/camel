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

import org.apache.camel.Service;

/**
 * Defines a strategy for handling resume operations. Implementations can define different ways to handle how to resume
 * processing records.
 */
public interface ResumeStrategy extends Service {
    String DEFAULT_NAME = "resumeStrategy";

    /**
     * Sets an adapter for resuming operations with this strategy
     * 
     * @param adapter the component-specific resume adapter
     */
    void setAdapter(ResumeAdapter adapter);

    /**
     * Gets an adapter for resuming operations
     */
    ResumeAdapter getAdapter();

    /**
     * Gets and adapter for resuming operations
     * 
     * @param  clazz the class of the adapter
     * @return       the adapter or null if it can't be cast to the requested class
     * @param  <T>   the type of the adapter
     */
    default <T extends ResumeAdapter> T getAdapter(Class<T> clazz) {
        return clazz.cast(getAdapter());
    }

    /**
     * Loads the cache with the data currently available in this strategy
     * 
     * @throws Exception
     */
    default void loadCache() throws Exception {

    }

    /**
     * Updates the last processed offset
     *
     * @param  offset    the offset to update
     * @throws Exception if unable to update the offset
     */
    <T extends Resumable> void updateLastOffset(T offset) throws Exception;

    /**
     * Updates the last processed offset
     *
     * @param  offset    the offset key to update
     * @param  offset    the offset value to update
     * @throws Exception if unable to update the offset
     */
    void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offset) throws Exception;
}
