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
package org.apache.camel.test.dst;

import java.util.ArrayList;
import java.util.List;

/**
 * What the fakes saw, in order: a message offered by the source, routed, completed, acknowledged (checkpointed,
 * committed). The invariants of {@link Invariants} are written against it, so they are reusable across components.
 */
public final class DstLedger {

    /** One entry. {@code group} is for example a partition, {@code key} a message id. */
    public record Entry(long ordinal, long time, String kind, String group, String key) {
        @Override
        public String toString() {
            return "#" + ordinal + " t=" + time + " " + kind + " " + group + "/" + key;
        }
    }

    private final Dst dst;
    private final List<Entry> entries = new ArrayList<>();

    public DstLedger(Dst dst) {
        this.dst = dst;
    }

    public Entry record(String kind, String group, String key) {
        Entry e = new Entry(entries.size(), dst.now(), kind, group, key);
        entries.add(e);
        dst.trace("ledger " + kind + " " + group + "/" + key);
        return e;
    }

    public List<Entry> entries() {
        return entries;
    }

    public List<Entry> of(String kind) {
        List<Entry> answer = new ArrayList<>();
        for (Entry e : entries) {
            if (e.kind().equals(kind)) {
                answer.add(e);
            }
        }
        return answer;
    }

    public List<Entry> of(String kind, String group) {
        List<Entry> answer = new ArrayList<>();
        for (Entry e : entries) {
            if (e.kind().equals(kind) && e.group().equals(group)) {
                answer.add(e);
            }
        }
        return answer;
    }
}
