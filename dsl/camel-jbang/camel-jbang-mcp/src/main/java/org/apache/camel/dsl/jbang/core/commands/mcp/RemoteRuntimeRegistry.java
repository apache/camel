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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * Registry of remote integrations. Identity is the authenticated owner namespace plus executionId, never the remote
 * PID. Disconnected entries retain bounded last-known snapshots for a reconnect window.
 */
@ApplicationScoped
public class RemoteRuntimeRegistry {

    private static final Logger LOG = Logger.getLogger(RemoteRuntimeRegistry.class);
    private static final long RESERVATION_TTL_MILLIS = 30_000L;
    private static final int DEFAULT_MAX_CONNECTIONS = 16;
    private static final int DEFAULT_MAX_RETAINED_RUNTIMES = 32;
    private static final long DEFAULT_RETENTION_MILLIS = 3_600_000L;
    private static final long DEFAULT_HEARTBEAT_TIMEOUT_MILLIS = 90_000L;

    @Inject
    RemoteRuntimeConfig config;

    private final ConcurrentMap<Key, RemoteRuntimeEntry> entries = new ConcurrentHashMap<>();
    private final Map<String, Reservation> reservations = new HashMap<>();

    /** Reserves a capacity slot before the HTTP upgrade completes. Abandoned reservations expire automatically. */
    synchronized String reserve(String ownerId, String executionId) {
        cleanup();
        Key key = new Key(ownerId, executionId);
        Set<Key> occupied = new HashSet<>();
        entries.forEach((entryKey, entry) -> {
            if (entry.isConnected()) {
                occupied.add(entryKey);
            }
        });
        reservations.values().forEach(reservation -> occupied.add(reservation.key()));
        // Only one pending upgrade may claim a given logical runtime at a time. An already-connected key may
        // reconnect once, replacing its previous socket after the new handshake is accepted.
        boolean keyReserved = reservations.values().stream().anyMatch(reservation -> reservation.key().equals(key));
        if (keyReserved) {
            return null;
        }
        RemoteRuntimeEntry existing = entries.get(key);
        boolean replacingConnection = existing != null && existing.isConnected();
        if (!replacingConnection && occupied.size() >= maxConnections()) {
            return null;
        }
        if (existing == null && entries.size() >= maxRetainedRuntimes()) {
            evictOldestDisconnected();
            if (entries.size() >= maxRetainedRuntimes()) {
                return null;
            }
        }
        String id = UUID.randomUUID().toString();
        reservations.put(id, new Reservation(key, System.currentTimeMillis() + RESERVATION_TTL_MILLIS));
        return id;
    }

    /** Claims a pre-upgrade reservation and attaches the newly opened socket to the stable runtime entry. */
    synchronized RemoteRuntimeEntry claim(String reservationId, RemoteRuntimeConnection connection) {
        cleanup();
        Reservation reservation = reservations.remove(reservationId);
        if (reservation == null) {
            throw new IllegalStateException("The WebSocket connection reservation is missing or expired");
        }
        Key key = reservation.key();
        RemoteRuntimeEntry entry = entries.computeIfAbsent(key, k -> new RemoteRuntimeEntry(
                k.ownerId(), k.executionId(), maxSnapshotChars(), maxRetainedMessages()));
        RemoteRuntimeConnection previous = entry.attach(connection);
        connection.attach(entry);
        if (previous != null && previous != connection) {
            previous.closeReplaced();
            LOG.debugf("Replaced an older WebSocket session for remote integration %s", key.executionId());
        }
        return entry;
    }

    synchronized void cancelReservation(String reservationId) {
        if (reservationId != null) {
            reservations.remove(reservationId);
        }
    }

    /**
     * Detaches only the session that is still current; a late close from a replaced socket cannot remove its successor.
     */
    void disconnect(RemoteRuntimeEntry entry, RemoteRuntimeConnection connection) {
        if (entry != null && connection != null && entry.detach(connection)) {
            connection.markClosed("Remote integration disconnected");
            LOG.debugf("Remote integration disconnected: %s", entry.executionId());
        }
    }

    RemoteRuntimeEntry find(String ownerId, String executionId) {
        cleanup();
        return entries.get(new Key(ownerId, executionId));
    }

    List<RemoteRuntimeEntry> list(String ownerId) {
        cleanup();
        List<RemoteRuntimeEntry> result = new ArrayList<>();
        entries.forEach((key, value) -> {
            if (key.ownerId().equals(ownerId)) {
                result.add(value);
            }
        });
        result.sort((a, b) -> a.executionId().compareTo(b.executionId()));
        return List.copyOf(result);
    }

    int connectedCount() {
        cleanup();
        return (int) entries.values().stream().filter(RemoteRuntimeEntry::isConnected).count();
    }

    private synchronized void cleanup() {
        long now = System.currentTimeMillis();
        reservations.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        entries.values().forEach(entry -> {
            RemoteRuntimeConnection expired = entry.expireConnectionIfIdle(now, heartbeatTimeoutMillis());
            if (expired != null) {
                expired.closeConnection("Remote integration heartbeat timed out");
                LOG.debugf("Expired idle remote integration connection: %s", entry.executionId());
            }
        });
        pruneDisconnected();
    }

    private void pruneDisconnected() {
        long cutoff = System.currentTimeMillis() - retentionMillis();
        entries.entrySet().removeIf(e -> !e.getValue().isConnected() && e.getValue().lastSeen() < cutoff);
    }

    /** Evicts the oldest disconnected entry when the configured registry bound is reached. */
    private void evictOldestDisconnected() {
        Set<Key> reservedKeys = new HashSet<>();
        reservations.values().forEach(reservation -> reservedKeys.add(reservation.key()));
        Map.Entry<Key, RemoteRuntimeEntry> oldest = entries.entrySet().stream()
                .filter(e -> !e.getValue().isConnected() && !reservedKeys.contains(e.getKey()))
                .min((a, b) -> Long.compare(a.getValue().lastSeen(), b.getValue().lastSeen()))
                .orElse(null);
        if (oldest != null) {
            entries.remove(oldest.getKey(), oldest.getValue());
        }
    }

    private int maxConnections() {
        return config != null ? config.maxConnections() : DEFAULT_MAX_CONNECTIONS;
    }

    private int maxRetainedRuntimes() {
        return config != null ? config.maxRetainedRuntimes() : DEFAULT_MAX_RETAINED_RUNTIMES;
    }

    private long retentionMillis() {
        return config != null ? config.retentionMillis() : DEFAULT_RETENTION_MILLIS;
    }

    private long heartbeatTimeoutMillis() {
        return config != null ? config.heartbeatTimeoutMillis() : DEFAULT_HEARTBEAT_TIMEOUT_MILLIS;
    }

    private int maxSnapshotChars() {
        return config != null ? config.maxSnapshotChars() : 1024 * 1024;
    }

    private int maxRetainedMessages() {
        return config != null ? config.maxRetainedMessages() : 500;
    }

    @PreDestroy
    synchronized void closeAll() {
        entries.values().forEach(entry -> {
            // Marking the current session closed also fails every in-flight action without replaying it.
            try {
                RemoteRuntimeConnection current = currentConnection(entry);
                if (current != null) {
                    current.closeConnection("Camel MCP server is shutting down");
                    entry.detach(current);
                }
            } catch (Exception e) {
                LOG.debug("Error closing remote integration during shutdown", e);
            }
        });
        entries.clear();
        reservations.clear();
    }

    private static RemoteRuntimeConnection currentConnection(RemoteRuntimeEntry entry) {
        return entry.connectionForShutdown();
    }

    private record Key(String ownerId, String executionId) {
    }

    private record Reservation(Key key, long expiresAt) {
    }
}
