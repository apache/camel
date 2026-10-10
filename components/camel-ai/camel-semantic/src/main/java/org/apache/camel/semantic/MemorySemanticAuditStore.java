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
package org.apache.camel.semantic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.camel.support.service.ServiceSupport;

/** Bounded recent history, lost when the context is destroyed. No persistent-storage guarantee. */
public final class MemorySemanticAuditStore extends ServiceSupport implements SemanticAuditSink, SemanticAuditReader {
    private final int capacity;
    private final String epoch = UUID.randomUUID().toString();
    private final Deque<Entry> records = new ArrayDeque<>();
    private long sequence;
    private long evicted;

    public MemorySemanticAuditStore(int capacity) {
        if (capacity < 1 || capacity > 100000) {
            throw new IllegalArgumentException("Invalid audit capacity");
        }
        this.capacity = capacity;
    }

    @Override
    public synchronized void append(SemanticAuditRecord record) {
        if (records.size() == capacity) {
            records.removeLast();
            evicted++;
        }
        records.addFirst(new Entry(++sequence, record));
    }

    @Override
    public synchronized Optional<SemanticAuditRecord> get(String eventId) {
        return records.stream().map(Entry::record).filter(r -> r.getEventId().equals(eventId)).findFirst();
    }

    @Override
    public synchronized Map<String, SemanticAuditRecord> getAll(List<String> eventIds) {
        if (eventIds.size() > 100) {
            throw new IllegalArgumentException("Too many audit evidence references");
        }
        var selected = new HashSet<>(eventIds);
        Map<String, SemanticAuditRecord> found = new LinkedHashMap<>();
        for (Entry entry : records) {
            if (selected.remove(entry.record.getEventId())) {
                found.put(entry.record.getEventId(), entry.record);
            }
        }
        return Map.copyOf(found);
    }

    @Override
    public SemanticAuditPage query(SemanticAuditQuery query) {
        long before = Long.MAX_VALUE;
        if (query.getCursor() != null) {
            String[] parts = query.getCursor().split(":", -1);
            if (parts.length != 3 || !epoch.equals(parts[0])
                    || !query.fingerprint().equals(parts[2])) {
                throw new IllegalArgumentException("Audit cursor does not match this store or query");
            }
            try {
                before = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid audit cursor", e);
            }
        }
        List<Entry> snapshot;
        long evictions;
        synchronized (this) {
            snapshot = List.copyOf(records);
            evictions = evicted;
        }
        boolean expired
                = before != Long.MAX_VALUE && !snapshot.isEmpty() && before <= snapshot.get(snapshot.size() - 1).sequence;
        List<SemanticAuditRecord> page = new ArrayList<>();
        long last = 0;
        boolean more = false;
        for (Entry entry : snapshot) {
            if (entry.sequence < before && query.matches(entry.record)) {
                if (page.size() == query.getLimit()) {
                    more = true;
                    break;
                }
                page.add(entry.record);
                last = entry.sequence;
            }
        }
        String next
                = more ? epoch + ":" + last + ":" + query.fingerprint() : null;
        return new SemanticAuditPage(page, next, evictions, expired);
    }

    public int getCapacity() {
        return capacity;
    }

    public synchronized int size() {
        return records.size();
    }

    private record Entry(long sequence, SemanticAuditRecord record) {
    }
}
