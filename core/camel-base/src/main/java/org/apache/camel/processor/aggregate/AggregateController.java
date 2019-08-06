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
package org.apache.camel.processor.aggregate;

/**
 * A controller which allows controlling a {@link org.apache.camel.processor.aggregate.AggregateProcessor} from an
 * external source, such as Java API or JMX. This can be used to force completion of aggregated groups, and more.
 */
public interface AggregateController {

    /**
     * Callback when the aggregate processor is started.
     *
     * @param processor the aggregate processor
     */
    void onStart(AggregateProcessor processor);

    /**
     * Callback when the aggregate processor is stopped.
     *
     * @param processor the aggregate processor
     */
    void onStop(AggregateProcessor processor);

    /**
     * To force completing a specific group by its key.
     *
     * @param key the key
     * @return <tt>1</tt> if the group was forced completed, <tt>0</tt> if the group does not exists
     */
    int forceCompletionOfGroup(String key);

    /**
     * To force complete of all groups
     *
     * @return number of groups that was forced completed
     */
    int forceCompletionOfAllGroups();

    /**
     * To force discarding a specific group by its key.
     *
     * @param key the key
     * @return <tt>1</tt> if the group was forced discarded, <tt>0</tt> if the group does not exists
     */
    int forceDiscardingOfGroup(String key);

    /**
     * To force discardingof all groups
     *
     * @return number of groups that was forced discarded
     */
    int forceDiscardingOfAllGroups();

}
