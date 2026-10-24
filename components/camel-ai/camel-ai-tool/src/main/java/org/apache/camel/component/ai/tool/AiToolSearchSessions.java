
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
package org.apache.camel.component.ai.tool;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Conversation-scoped {@link AiToolSearchSession}s.
 * <p>
 * Sessions are retained only after a tool has been discovered. Retained sessions expire after a period of inactivity
 * and are subject to a maximum size. Access and mutation are synchronized so expiration and capacity eviction cannot
 * corrupt the session catalogue.
 * <p>
 * Expired entries are removed during subsequent operations; no background thread is required. Callers can explicitly
 * remove a conversation or clear all retained sessions when their conversation or chat-memory lifecycle ends.
 *
 * @since 4.24
 */
public final class AiToolSearchSessions {

    static final int DEFAULT_MAX_SESSIONS = 10_000;
    static final long DEFAULT_EXPIRATION_MILLIS = TimeUnit.MINUTES.toMillis(30);

    private final int maxSessions;
    private final long expirationNanos;
    private final LongSupplier nanoClock;

    /*
     * Access-order LinkedHashMap provides least-recently-used eviction.
     * All access is guarded by this instance's monitor.
     */
    private final LinkedHashMap<String, Entry> sessions = new LinkedHashMap<>(16, 0.75f, true);

    public AiToolSearchSessions() {
        this(DEFAULT_MAX_SESSIONS, DEFAULT_EXPIRATION_MILLIS, System::nanoTime);
    }

    AiToolSearchSessions(int maxSessions, long expirationMillis) {
        this(maxSessions, expirationMillis, System::nanoTime);
    }

    AiToolSearchSessions(int maxSessions, long expirationMillis, LongSupplier nanoClock) {
        if (maxSessions < 1) {
            throw new IllegalArgumentException("maxSessions must be greater than zero");
        }
        if (expirationMillis < 1) {
            throw new IllegalArgumentException("expirationMillis must be greater than zero");
        }
        this.maxSessions = maxSessions;
        this.expirationNanos = TimeUnit.MILLISECONDS.toNanos(expirationMillis);
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    /**
     * Returns the session for a conversation id.
     * <p>
     * An unregistered session is returned without retaining it. It is registered only when a tool is remembered.
     *
     * @param  conversationId stable id for one conversation; must not be blank
     * @return                the session for that id
     */
    public synchronized AiToolSearchSession session(String conversationId) {
        validateConversationId(conversationId);

        long now = nanoClock.getAsLong();
        purgeExpired(now);

        Entry entry = sessions.get(conversationId);
        if (entry != null) {
            entry.lastAccessNanos = now;
            return entry.session;
        }

        return new AiToolSearchSession(this, conversationId);
    }

    /**
     * Removes the retained session for a conversation.
     *
     * @param  conversationId conversation id
     * @return                true if a retained session was removed
     */
    /**
     * Finds an existing retained session without creating a new one.
     */
    public synchronized AiToolSearchSession find(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }

        long now = nanoClock.getAsLong();
        purgeExpired(now);

        Entry entry = sessions.get(conversationId);
        if (entry == null) {
            return null;
        }

        entry.lastAccessNanos = now;
        return entry.session;
    }

    public synchronized boolean remove(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return false;
        }
        purgeExpired(nanoClock.getAsLong());
        return sessions.remove(conversationId) != null;
    }

    /**
     * Removes all retained sessions.
     */
    public synchronized void clear() {
        sessions.clear();
    }

    /**
     * Records discovered tool names, registering the session only when names are present.
     */
    synchronized void remember(String conversationId, Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return;
        }

        long now = nanoClock.getAsLong();
        purgeExpired(now);

        Entry entry = sessions.get(conversationId);
        if (entry == null) {
            AiToolSearchSession stored = new AiToolSearchSession(this, conversationId);
            stored.rememberLocal(names);
            sessions.put(conversationId, new Entry(stored, now));
            evictExcessSessions();
        } else {
            entry.session.rememberLocal(names);
            entry.lastAccessNanos = now;
        }
    }

    synchronized List<String> discoveredNames(String conversationId) {
        long now = nanoClock.getAsLong();
        purgeExpired(now);

        Entry entry = sessions.get(conversationId);
        if (entry == null) {
            return List.of();
        }

        entry.lastAccessNanos = now;
        return entry.session.discoveredNamesLocal();
    }

    synchronized void retainAvailable(String conversationId, Set<String> availableNames) {
        long now = nanoClock.getAsLong();
        purgeExpired(now);

        Entry entry = sessions.get(conversationId);
        if (entry == null) {
            return;
        }

        entry.session.retainAvailableLocal(availableNames);
        if (entry.session.discoveredNamesLocal().isEmpty()) {
            sessions.remove(conversationId);
        } else {
            entry.lastAccessNanos = now;
        }
    }

    synchronized int size() {
        purgeExpired(nanoClock.getAsLong());
        return sessions.size();
    }

    private void purgeExpired(long now) {
        Iterator<Map.Entry<String, Entry>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (now - entry.lastAccessNanos < expirationNanos) {
                // The map is in access order, so all remaining entries were accessed even more recently.
                break;
            }
            iterator.remove();
        }
    }

    private void evictExcessSessions() {
        Iterator<String> iterator = sessions.keySet().iterator();
        while (sessions.size() > maxSessions && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static void validateConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("A conversation id is required for a shared tool-search session");
        }
    }

    private static final class Entry {
        private final AiToolSearchSession session;
        private long lastAccessNanos;

        private Entry(AiToolSearchSession session, long lastAccessNanos) {
            this.session = session;
            this.lastAccessNanos = lastAccessNanos;
        }
    }
}
