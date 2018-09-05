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
package org.apache.camel.component.jcache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.EventType;

class JCacheEntryEventFilters {
    public static class Named implements CacheEntryEventFilter<Object, Object> {
        private List<EventType> filteredEvents;

        Named(Collection<EventType> filteredEventNames) {
            if (filteredEventNames != null && !filteredEventNames.isEmpty()) {
                this.filteredEvents = new ArrayList<>(filteredEventNames);
            }
        }

        @Override
        public boolean evaluate(CacheEntryEvent<?, ?> event) throws CacheEntryListenerException {
            if (filteredEvents == null) {
                return true;
            }

            return !filteredEvents.contains(event.getEventType());
        }
    }

    public static class Chained implements CacheEntryEventFilter<Object, Object> {
        private final List<CacheEntryEventFilter> filteredEvents;
        private final int filteredEventsSize;

        Chained(List<CacheEntryEventFilter> filteredEvents) {
            if (filteredEvents != null && !filteredEvents.isEmpty()) {
                this.filteredEvents = new ArrayList<>(filteredEvents);
                this.filteredEventsSize = this.filteredEvents.size();
            } else {
                this.filteredEvents = null;
                this.filteredEventsSize = 0;
            }
        }

        @Override
        public boolean evaluate(CacheEntryEvent<?, ?> event) throws CacheEntryListenerException {
            if (filteredEvents == null) {
                return true;
            }

            for (int i = 0; i < filteredEventsSize; i++) {
                if (!filteredEvents.get(i).evaluate(event)) {
                    return false;
                }
            }

            return true;
        }
    }
}
