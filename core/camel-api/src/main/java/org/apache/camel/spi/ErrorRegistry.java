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
package org.apache.camel.spi;

import java.time.Duration;

import org.apache.camel.StaticService;

/**
 * A registry which captures exceptions that occurred during message routing and stores them in memory.
 * <p/>
 * This is an opt-in feature that must be enabled. When enabled, the registry captures error snapshots (exception type,
 * message, stack trace) without retaining references to the original exchange or exception objects.
 * <p/>
 * The registry has a configurable maximum capacity and time-to-live to prevent unbounded memory growth and stale data.
 * <p/>
 * The registry itself implements {@link ErrorRegistryView} for global scope, and scoped views for individual routes can
 * be obtained via {@link #forRoute(String)}.
 *
 * @see ErrorRegistryEntry
 * @see ErrorRegistryView
 */
public interface ErrorRegistry extends ErrorRegistryView, StaticService {

    /**
     * Gets a view scoped to a specific route.
     * <p/>
     * The returned view is a lightweight filter over the same underlying data.
     *
     * @param  routeId the route id
     * @return         a view containing only errors from the given route
     */
    ErrorRegistryView forRoute(String routeId);

    // -- Configuration --

    /**
     * Whether the error registry is enabled
     */
    boolean isEnabled();

    /**
     * Sets whether the error registry is enabled.
     * <p/>
     * This is by default disabled.
     */
    void setEnabled(boolean enabled);

    /**
     * The maximum number of error entries to keep in the registry
     */
    int getMaximumEntries();

    /**
     * Sets the maximum number of error entries to keep. When the limit is exceeded, the oldest entries are evicted.
     * <p/>
     * The default value is 100.
     */
    void setMaximumEntries(int maximumEntries);

    /**
     * The time-to-live for error entries
     */
    Duration getTimeToLive();

    /**
     * Sets the time-to-live for error entries. Entries older than this duration are evicted.
     * <p/>
     * The default value is 1 hour.
     */
    void setTimeToLive(Duration timeToLive);

    /**
     * Whether stack trace capture is enabled
     */
    boolean isStackTraceEnabled();

    /**
     * Sets whether to capture stack traces. This is disabled by default to reduce memory usage.
     */
    void setStackTraceEnabled(boolean stackTraceEnabled);
}
