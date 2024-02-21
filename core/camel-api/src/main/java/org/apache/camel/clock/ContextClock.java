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

package org.apache.camel.clock;

import java.util.Date;
import java.util.EnumMap;

import org.apache.camel.ContextEvents;

/**
 * An event clock that tracks the pass of time for different types of context-related events (see {@link ContextEvents})
 */
public final class ContextClock implements EventClock<ContextEvents> {
    private EnumMap<ContextEvents, Clock> events = new EnumMap<>(ContextEvents.class);

    @Override
    public long elapsed() {
        return 0;
    }

    @Override
    public long getCreated() {
        return 0;
    }

    @Override
    public void add(ContextEvents event, Clock clock) {
        events.put(event, clock);
    }

    @Override
    public Clock get(ContextEvents event) {
        return events.get(event);
    }

    /**
     * Get the elapsed time for the event
     *
     * @param  event        the event to get the elapsed time
     * @param  defaultValue the default value to provide if the event is not being tracked
     * @return              The elapsed time or the default value if the event is not being tracked
     */
    public long elapsed(ContextEvents event, long defaultValue) {
        Clock clock = events.get(event);
        if (clock == null) {
            return defaultValue;
        }

        return clock.elapsed();
    }

    /**
     * Get the time for the event as a Date object
     *
     * @param  event        the event to get the elapsed time
     * @param  defaultValue the default value to provide if the event is not being tracked
     * @return              The Date object representing the creation date or the default value if the event is not
     *                      being tracked
     */
    public Date asDate(ContextEvents event, Date defaultValue) {
        Clock clock = events.get(event);
        if (clock == null) {
            return defaultValue;
        }

        return clock.asDate();
    }
}
