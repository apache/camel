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
package org.apache.camel.opentracing.concurrent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.opentracing.NoopSpan;
import io.opentracing.Span;
import io.opentracing.contrib.spanmanager.SpanManager;

/**
 * Camel {@link SpanManager} implementation using {@link ThreadLocal} storage
 * maintaining a stack-like structure of linked managed spans.
 * <p>
 * The linked managed spans provide the following stack unwinding algorithm:
 * <ol>
 * <li>If the deactivated span is not the <em>managed</em> span, the <em>current managed</em> span is left alone.</li>
 * <li>Otherwise, the first parent that is <em>not yet deactivated</em> is set as the new managed span.</li>
 * <li>If no managed parents remain, the <em>managed span</em> is cleared.</li>
 * <li>Consecutive <code>deactivate()</code> calls for already-deactivated spans will be ignored.</li>
 * </ol>
 * <p>
 * NOTE: This implementation has been copied and extended from opentracing-contrib/java-spanmanager
 * project, to provide the additional functionality for transferring the managed spans from one thread
 * to another. This functionality will soon be provided by the core opentracing-java project, at which
 * time this implementation will be removed.
 */
public final class CamelSpanManager implements SpanManager {

    private static final Logger LOGGER = Logger.getLogger(CamelSpanManager.class.getName());
    private static final CamelSpanManager INSTANCE = new CamelSpanManager();
    private static final ManagedSpan NO_MANAGED_SPAN = new NoManagedSpan();

    private final ThreadLocal<LinkedManagedSpan> managed = new ThreadLocal<LinkedManagedSpan>();

    private CamelSpanManager() {
    }

    /**
     * @return The singleton instance of the camel span manager.
     */
    public static CamelSpanManager getInstance() {
        return INSTANCE;
    }

    /**
     * Stack unwinding algorithm that refreshes the currently managed span.
     * <p>
     * See {@link CamelSpanManager class javadoc} for a full description.
     *
     * @return The current non-deactivated LinkedManagedSpan or <code>null</code> if none remained.
     */
    private LinkedManagedSpan refreshCurrent() {
        LinkedManagedSpan managedSpan = managed.get();
        LinkedManagedSpan current = managedSpan;
        while (current != null && current.deactivated.get()) { // Unwind stack if necessary.
            current = current.parent;
        }
        if (current != managedSpan) { // refresh current if necessary.
            if (current == null) {
                managed.remove();
            } else {
                managed.set(current);
            }
        }
        return current;
    }

    @Override
    public ManagedSpan activate(Span span) {
        LinkedManagedSpan managedSpan = new LinkedManagedSpan(span, refreshCurrent());
        managed.set(managedSpan);
        return managedSpan;
    }

    /**
     * This method associates the supplied managed span with the current
     * execution context (thread).
     *
     * @param managedSpan The managed span
     */
    public void activate(ManagedSpan managedSpan) {
        if (managedSpan instanceof LinkedManagedSpan) {
            managed.set((LinkedManagedSpan)managedSpan);
        }
    }

    @Override
    public ManagedSpan current() {
        LinkedManagedSpan current = refreshCurrent();
        return current != null ? current : NO_MANAGED_SPAN;
    }

    @Override
    public void clear() {
        managed.remove();
    }

    @Override
    @Deprecated
    public Span currentSpan() {
        ManagedSpan current = current();
        return current.getSpan() != null ? current.getSpan() : NoopSpan.INSTANCE;
    }

    /**
     * @see #activate(Span)
     * @deprecated renamed to activate()
     */
    @Deprecated
    public ManagedSpan manage(Span span) {
        return activate(span);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    private final class LinkedManagedSpan implements ManagedSpan {
        private final LinkedManagedSpan parent;
        private final Span span;
        private final AtomicBoolean deactivated = new AtomicBoolean(false);

        private LinkedManagedSpan(Span span, LinkedManagedSpan parent) {
            this.parent = parent;
            this.span = span;
        }

        @Override
        public Span getSpan() {
            return span;
        }

        public void deactivate() {
            if (deactivated.compareAndSet(false, true)) {
                LinkedManagedSpan current = refreshCurrent(); // Trigger stack-unwinding algorithm.
                LOGGER.log(Level.FINER, "Released {0}, current span is {1}.", new Object[]{this, current});
            } else {
                LOGGER.log(Level.FINEST, "No action needed, {0} was already deactivated.", this);
            }
        }

        @Override
        public void close() {
            deactivate();
        }

        /**
         * @see #deactivate()
         * @deprecated renamed to deactivate()
         */
        @Deprecated
        public void release() {
            deactivate();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + '{' + span + '}';
        }
    }

    /**
     * Empty implementation signifying there is no managed span.
     */
    private static final class NoManagedSpan implements ManagedSpan {
        private NoManagedSpan() {
        }

        @Override
        public Span getSpan() {
            return null;
        }

        @Override
        public void deactivate() {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }

        @Override
        public String toString() {
            return getClass().getSimpleName();
        }
    }
}
