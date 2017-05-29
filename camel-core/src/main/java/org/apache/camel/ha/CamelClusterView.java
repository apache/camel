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
package org.apache.camel.ha;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Represents the View of the cluster at some given period of time.
 */
public interface CamelClusterView {

    enum Event {
        KEEP_ALIVE,
        LEADERSHIP_CHANGED;
    }

    /**
     * @return the cluster.
     */
    CamelCluster getCluster();

    /**
     * @return the namespace for this view.
     */
    String getNamespace();

    /**
     * Provides the master member.
     *
     * @return the master member.
     */
    CamelClusterMember getMaster();

    /**
     * Provides the local member.
     *
     * @return the local member.
     */
    CamelClusterMember getLocalMember();

    /**
     * Provides the list of members of the cluster.
     *
     * @return the list of members.
     */
    List<CamelClusterMember> getMembers();

    /**
     * Add an event consumer.
     *
     * @param consumer the event consumer.
     */
    void addEventListener(BiConsumer<Event, Object> consumer);

    /**
     * Add an event consumer for events matching the given predicate.
     *
     * @param predicate the predicate to filter events.
     * @param consumer the event consumer.
     */
    void addEventListener(Predicate<Event> predicate, BiConsumer<Event, Object> consumer);

    /**
     * Remove the event consumer.
     *
     * @param event the event consumer.
     */
    void removeEventListener(BiConsumer<Event, Object> event);
}
