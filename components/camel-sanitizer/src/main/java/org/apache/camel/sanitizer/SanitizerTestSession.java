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
package org.apache.camel.sanitizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * The tie between a test class and the CamelContexts built for it, used by the JUnit extension
 * ({@link org.apache.camel.sanitizer.junit.CamelSanitizer}). Internal: it is public only for the extension, and is not
 * an API.
 * <p/>
 * The extension opens a session for a test class and marks the thread that runs the class with it. A CamelContext built
 * on a marked thread gets the sanitizer (unless {@code camel.sanitizer.enabled=false} or profile {@code prod}), and its
 * violations are charged to the session. When the class ends the session is closed: the contexts tied to it are untied
 * then. The violations of a context that is not tied to an open session are charged to every session open when they
 * happen. Nothing is set for the whole JVM, so test classes that run in parallel do not see each other's violations,
 * except the ones of contexts that are not tied.
 */
public final class SanitizerTestSession {

    private static final ThreadLocal<SanitizerTestSession> CURRENT = new ThreadLocal<>();
    private static final Set<SanitizerTestSession> OPEN = ConcurrentHashMap.newKeySet();

    private final String name;
    private final ConcurrentLinkedQueue<SanitizerViolation> violations = new ConcurrentLinkedQueue<>();
    // how many violations wait for drain(): at most Sanitizer.MAX_KEPT are kept, so a route that breaks the rule at each
    // message does not fill the memory; the test fails with the first ones
    private final AtomicInteger pending = new AtomicInteger();
    private volatile boolean closed;
    private volatile boolean installed;
    private volatile boolean turnedOff;

    private SanitizerTestSession(String name) {
        this.name = name;
    }

    /**
     * Opens a session.
     *
     * @param name the name of the session, such as the name of the test class
     */
    public static SanitizerTestSession open(String name) {
        SanitizerTestSession session = new SanitizerTestSession(name);
        OPEN.add(session);
        return session;
    }

    /**
     * The session that marks the current thread, or {@code null}.
     */
    static SanitizerTestSession current() {
        return CURRENT.get();
    }

    /**
     * Marks the current thread with this session.
     *
     * @return the session that marked the thread before, to give to {@link #restore(SanitizerTestSession)}
     */
    public SanitizerTestSession mark() {
        SanitizerTestSession before = CURRENT.get();
        CURRENT.set(this);
        return before;
    }

    /**
     * Marks the current thread again with the session it had before {@link #mark()}.
     */
    public static void restore(SanitizerTestSession before) {
        if (before != null) {
            CURRENT.set(before);
        } else {
            CURRENT.remove();
        }
    }

    /**
     * Takes the violations charged to this session since the last call.
     */
    public List<SanitizerViolation> drain() {
        List<SanitizerViolation> answer = new ArrayList<>();
        for (SanitizerViolation v = violations.poll(); v != null; v = violations.poll()) {
            answer.add(v);
            pending.decrementAndGet();
        }
        return answer;
    }

    /**
     * Closes the session: the contexts tied to it are untied. A violation charged before is kept for {@link #drain()};
     * one charged after goes to the open sessions.
     */
    public void close() {
        synchronized (this) {
            closed = true;
        }
        OPEN.remove(this);
    }

    /**
     * Whether the sanitizer was installed in a context tied to this session.
     */
    public boolean isInstalled() {
        return installed;
    }

    void installed() {
        installed = true;
    }

    /**
     * Whether a context tied to this session stayed off on purpose: profile {@code prod},
     * {@code camel.sanitizer.enabled=false}, pooled exchanges or no check.
     */
    public boolean isTurnedOff() {
        return turnedOff;
    }

    void turnedOff() {
        turnedOff = true;
    }

    boolean isClosed() {
        return closed;
    }

    String getName() {
        return name;
    }

    /**
     * Charges a violation of a context tied to this session; to every open session if this one is closed.
     */
    void charge(Supplier<SanitizerViolation> violation) {
        synchronized (this) {
            if (!closed) {
                add(violation);
                return;
            }
        }
        chargeUntied(violation);
    }

    /**
     * Charges a violation of a context that is not tied to an open session to every open session.
     */
    static void chargeUntied(Supplier<SanitizerViolation> violation) {
        for (SanitizerTestSession session : OPEN) {
            session.add(violation);
        }
    }

    /**
     * Keeps the violation for {@link #drain()}, unless 1000 already wait there: the test fails with those (the
     * violation is then not built).
     */
    private void add(Supplier<SanitizerViolation> violation) {
        if (pending.incrementAndGet() <= Sanitizer.MAX_KEPT) {
            violations.add(violation.get());
        } else {
            pending.decrementAndGet();
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
