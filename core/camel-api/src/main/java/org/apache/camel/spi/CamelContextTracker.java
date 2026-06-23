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
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the creation and destruction of {@link CamelContext} instances across the JVM.
 * <p/>
 * A tracker is activated by calling {@link #open()} (and deactivated via {@link #close()}); while open it is notified
 * through {@link #contextCreated(CamelContext)} and {@link #contextDestroyed(CamelContext)} for every context that
 * passes its {@link Filter} (by default, non-proxy contexts). Registration is global and static, making this suited to
 * cross-cutting concerns such as metrics or diagnostics that must observe all contexts rather than a single
 * {@link CamelContext}. Subclass and override the callbacks to react.
 */
public class CamelContextTracker implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(CamelContextTracker.class);

    private static final List<CamelContextTracker> TRACKERS = new CopyOnWriteArrayList<>();

    private static final Lock LOCK = new ReentrantLock();

    /**
     * Decides which {@link CamelContext} instances a {@link CamelContextTracker} is notified about.
     */
    @FunctionalInterface
    public interface Filter extends Predicate<CamelContext> {

        /**
         * Whether the given context should be tracked.
         *
         * @param  camelContext the camel context
         * @return              <tt>true</tt> to track the context
         */
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
        this.filter = Objects.requireNonNull(filter, "filter");
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
        Objects.requireNonNull(camelContext, "camelContext");
        // do nothing
    }

    /**
     * Called when a context has been shutdown.
     */
    public void contextDestroyed(CamelContext camelContext) {
        Objects.requireNonNull(camelContext, "camelContext");
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

    /**
     * Notifies all open trackers that the given {@link CamelContext} has been created. Called by Camel; exceptions from
     * trackers are logged and ignored.
     *
     * @param camelContext the created camel context
     */
    public static void notifyContextCreated(CamelContext camelContext) {
        Objects.requireNonNull(camelContext, "camelContext");
        LOCK.lock();
        try {
            for (CamelContextTracker tracker : TRACKERS) {
                try {
                    if (tracker.accept(camelContext)) {
                        tracker.contextCreated(camelContext);
                    }
                } catch (Exception e) {
                    LOG.warn("Error calling CamelContext tracker. This exception is ignored.", e);
                }
            }
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Notifies all open trackers that the given {@link CamelContext} has been destroyed. Called by Camel; exceptions
     * from trackers are logged and ignored.
     *
     * @param camelContext the destroyed camel context
     */
    public static void notifyContextDestroyed(CamelContext camelContext) {
        Objects.requireNonNull(camelContext, "camelContext");
        LOCK.lock();
        try {
            for (CamelContextTracker tracker : TRACKERS) {
                try {
                    if (tracker.accept(camelContext)) {
                        tracker.contextDestroyed(camelContext);
                    }
                } catch (Exception e) {
                    LOG.warn("Error calling CamelContext tracker. This exception is ignored.", e);
                }
            }
        } finally {
            LOCK.unlock();
        }
    }
}
