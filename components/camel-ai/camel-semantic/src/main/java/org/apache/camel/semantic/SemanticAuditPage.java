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

import java.util.List;

/** A newest-first page. Evictions and cursor expiration are visible instead of silently skipping history. */
public final class SemanticAuditPage {
    private final List<SemanticAuditRecord> records;
    private final String nextCursor;
    private final long evicted;
    private final boolean cursorExpired;

    public SemanticAuditPage(List<SemanticAuditRecord> records, String nextCursor, long evicted, boolean cursorExpired) {
        this.records = List.copyOf(records);
        this.nextCursor = nextCursor;
        this.evicted = evicted;
        this.cursorExpired = cursorExpired;
    }

    public List<SemanticAuditRecord> getRecords() {
        return records;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public long getEvicted() {
        return evicted;
    }

    public boolean isCursorExpired() {
        return cursorExpired;
    }
}
