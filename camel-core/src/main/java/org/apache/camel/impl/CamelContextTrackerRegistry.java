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
package org.apache.camel.impl;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.CamelContextTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A registry for {@link CamelContextTracker}.
 */
public final class CamelContextTrackerRegistry {

    /**
     * The registry singleton
     */
    public static final CamelContextTrackerRegistry INSTANCE = new CamelContextTrackerRegistry();

    private static final Logger LOG = LoggerFactory.getLogger(CamelContextTrackerRegistry.class);

    private final Set<CamelContextTracker> trackers = new LinkedHashSet<CamelContextTracker>();

    private CamelContextTrackerRegistry() {
        // hide constructor
    }

    public synchronized void addTracker(CamelContextTracker tracker) {
        trackers.add(tracker);
    }

    public synchronized void removeTracker(CamelContextTracker tracker) {
        trackers.remove(tracker);
    }

    synchronized void contextCreated(CamelContext camelContext) {
        for (CamelContextTracker tracker : trackers) {
            try {
                if (tracker.accept(camelContext)) {
                    tracker.contextCreated(camelContext);
                }
            } catch (Exception e) {
                LOG.warn("Error calling CamelContext tracker. This exception is ignored.", e);
            }
        }
    }
}
