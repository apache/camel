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
package org.apache.camel.component.infinispan.embedded;

import java.util.Set;

import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.event.Event;

public final class InfinispanEmbeddedEventListeners {
    private InfinispanEmbeddedEventListeners() {
    }

    // ******************************************
    //
    // Clustered
    //
    // ******************************************

    @Listener(clustered = true, sync = false)
    public static class ClusteredAsync extends InfinispanEmbeddedEventListener {
        public ClusteredAsync(Set<Event.Type> eventTypes) {
            super(eventTypes);
        }
    }

    @Listener(clustered = true, sync = true)
    public static class ClusteredSync extends InfinispanEmbeddedEventListener {
        public ClusteredSync(Set<Event.Type> eventTypes) {
            super(eventTypes);
        }
    }

    // ******************************************
    //
    // Local
    //
    // ******************************************

    @Listener(clustered = false, sync = false)
    public static class LocalAsync extends InfinispanEmbeddedEventListener {
        public LocalAsync(Set<Event.Type> eventTypes) {
            super(eventTypes);
        }
    }

    @Listener(clustered = false, sync = true)
    public static class LocalSync extends InfinispanEmbeddedEventListener {
        public LocalSync(Set<Event.Type> eventTypes) {
            super(eventTypes);
        }
    }
}
