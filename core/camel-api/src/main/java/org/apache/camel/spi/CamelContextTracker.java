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

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link CamelContext} creation and destruction tracker.
 */
public class CamelContextTracker implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(CamelContextTracker.class);

    private static final List<CamelContextTracker> TRACKERS = new CopyOnWriteArrayList<>();

    @FunctionalInterface
    public interface Filter extends Predicate<CamelContext> {

        boolean accept(CamelContext camelContext);

        @Override
        default boolean test(CamelContext camelContext) {
            return accept(camelContext);
        }
    }

    private final Filter filter;

    public CamelContextTracker() {
        filter = camelContext -> !camelContext.getClass().getName().contains("Proxy");
    }

    public CamelContextTracker(Filter filter) {
        this.filter = filter;
    }

    /**
     * Called to determine whether this tracker should accept the given context.
     */
    public boolean accept(CamelContext camelContext) {
        return filter == null || filter.accept(camelContext);
    }

    /**
     * Called when a context is created.
     */
    public void contextCreated(CamelContext camelContext) {
        // do nothing
    }

    /**
     * Called when a context has been shutdown.
     */
    public void contextDestroyed(CamelContext camelContext) {
        // do nothing
    }

    /**
     * Opens the tracker to start tracking when new {@link CamelContext} is created or destroyed.
     */
    public final void open() {
        TRACKERS.add(this);
    }

    /**
     * Closes the tracker so it not longer tracks.
     */
    @Override
    public final void close() {
        TRACKERS.remove(this);
    }

    public static synchronized void notifyContextCreated(CamelContext camelContext) {
        for (CamelContextTracker tracker : TRACKERS) {
            try {
                if (tracker.accept(camelContext)) {
                    tracker.contextCreated(camelContext);
                }
            } catch (Exception e) {
                LOG.warn("Error calling CamelContext tracker. This exception is ignored.", e);
            }
        }
    }

    public static synchronized void notifyContextDestroyed(CamelContext camelContext) {
        for (CamelContextTracker tracker : TRACKERS) {
            try {
                if (tracker.accept(camelContext)) {
                    tracker.contextDestroyed(camelContext);
                }
            } catch (Exception e) {
                LOG.warn("Error calling CamelContext tracker. This exception is ignored.", e);
            }
        }
    }
}
