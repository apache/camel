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

/**
 * An updatable resume strategy
 *
 * @param <T> the type of the addressable value for the resumable object (for example, a file would use a Long value)
 */
public interface UpdatableConsumerResumeStrategy<T extends Resumable> {

    /**
     * Updates the last processed offset
     * 
     * @param  offset    the offset to update
     * @throws Exception if unable to update the offset
     */
    void updateLastOffset(T offset) throws Exception;
}
