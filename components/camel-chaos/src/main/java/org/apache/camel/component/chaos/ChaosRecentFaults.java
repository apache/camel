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
package org.apache.camel.component.chaos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.apache.camel.component.chaos.ChaosRule.Hook;

/**
 * The last faults the rules fired, for the chaos screen: a small ring, the oldest fault drops out when it is full.
 */
final class ChaosRecentFaults {

    // the most faults it keeps
    static final int SIZE = 20;

    private final Deque<Entry> entries = new ArrayDeque<>(SIZE);

    /**
     * A fault: when, the rule, the hook (null for a lifecycle rule), the route, the endpoint URI of a send or poll or
     * the id of a step (null at the entry of a route), and the exchange (null at a poll and for a lifecycle rule).
     */
    record Entry(long time, ChaosRule rule, Hook hook, String routeId, String where, String exchangeId) {
    }

    synchronized void add(long time, ChaosRule rule, Hook hook, String routeId, String where, String exchangeId) {
        if (entries.size() == SIZE) {
            entries.removeFirst();
        }
        entries.addLast(new Entry(time, rule, hook, routeId, where, exchangeId));
    }

    /** The faults, the latest first. */
    synchronized List<Entry> latestFirst() {
        List<Entry> answer = new ArrayList<>(entries.size());
        entries.descendingIterator().forEachRemaining(answer::add);
        return answer;
    }

    synchronized void clear() {
        entries.clear();
    }
}
