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

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;

/**
 * Reusable invariants over a {@link DstLedger} and a {@link CamelContext}.
 */
public final class Invariants {

    private Invariants() {
    }

    public static Invariant of(String name, Supplier<String> check) {
        return new Invariant() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String violation() {
                return check.get();
            }
        };
    }

    /** Every message the source offered was routed at least once (at-least-once delivery into the route). */
    public static Invariant everyOfferedRoutedAtLeastOnce(DstLedger ledger, String offered, String routed) {
        return of("every offered message is routed at least once", () -> {
            Set<String> seen = new LinkedHashSet<>();
            ledger.of(routed).forEach(e -> seen.add(e.group() + "/" + e.key()));
            Set<String> missing = new TreeSet<>();
            for (DstLedger.Entry e : ledger.of(offered)) {
                if (!seen.contains(e.group() + "/" + e.key())) {
                    missing.add(e.group() + "/" + e.key());
                }
            }
            return missing.isEmpty() ? null : missing.size() + " never routed: " + missing;
        });
    }

    /** No message is routed twice. */
    public static Invariant noDuplicates(DstLedger ledger, String routed) {
        return of("no message is routed twice", () -> {
            Map<String, Integer> count = new HashMap<>();
            ledger.of(routed).forEach(e -> count.merge(e.group() + "/" + e.key(), 1, Integer::sum));
            Set<String> twice = new TreeSet<>();
            count.forEach((k, v) -> {
                if (v > 1) {
                    twice.add(k + " x" + v);
                }
            });
            return twice.isEmpty() ? null : twice.toString();
        });
    }

    /**
     * When a group (partition) goes quiet, its last completed message is covered by an acknowledgement (checkpoint,
     * commit) issued after it.
     */
    public static Invariant lastCompletedAcknowledgedWhenQuiet(DstLedger ledger, String completed, String ack) {
        return of("every completed message is acknowledged when the partition goes quiet", () -> {
            Set<String> groups = new TreeSet<>();
            ledger.of(completed).forEach(e -> groups.add(e.group()));
            StringBuilder sb = new StringBuilder();
            for (String g : groups) {
                List<DstLedger.Entry> done = ledger.of(completed, g);
                DstLedger.Entry last = done.get(done.size() - 1);
                List<DstLedger.Entry> acks = ledger.of(ack, g);
                DstLedger.Entry lastAck = acks.isEmpty() ? null : acks.get(acks.size() - 1);
                if (lastAck == null || lastAck.ordinal() < last.ordinal()) {
                    int uncovered = 0;
                    for (DstLedger.Entry e : done) {
                        if (lastAck == null || e.ordinal() > lastAck.ordinal()) {
                            uncovered++;
                        }
                    }
                    sb.append(String.format("partition %s: %d completed after the last checkpoint request (%s), last "
                                            + "completed %s",
                            g, uncovered, lastAck, last));
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        });
    }

    /** Every completed message is covered by an acknowledgement issued after it within {@code bound} milliseconds. */
    public static Invariant acknowledgedWithin(DstLedger ledger, String completed, String ack, long bound) {
        return of("every completed message is acknowledged within " + bound + "ms", () -> {
            for (DstLedger.Entry e : ledger.of(completed)) {
                boolean covered = false;
                for (DstLedger.Entry a : ledger.of(ack, e.group())) {
                    if (a.ordinal() > e.ordinal() && a.time() <= e.time() + bound) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) {
                    return e + " has no acknowledgement within " + bound + "ms";
                }
            }
            return null;
        });
    }

    /** The route is started (for example: the consumer is alive after the faults cleared). */
    public static Invariant routeStarted(CamelContext context, String routeId) {
        return of("route " + routeId + " is started", () -> {
            ServiceStatus status = context.getRouteController().getRouteStatus(routeId);
            return status == ServiceStatus.Started ? null : "status is " + status;
        });
    }
}
