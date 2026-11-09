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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether each integration was ready and live, second by second over the last minutes, as the TUI read its health: a
 * check that went DOWN and came back between two looks at the Health tab is seen on its strip.
 */
final class HealthHistory {

    static final int SECONDS = 300;
    /** Not known for that second: before the TUI read the integration, or the integration did not say. */
    static final byte UNKNOWN = -1;
    static final byte READY = 1;
    static final byte LIVE = 2;

    // per pid: a ring of the last SECONDS seconds, and the second its last value is of
    private final Map<String, Ring> rings = new ConcurrentHashMap<>();

    private static final class Ring {
        final byte[] values = new byte[SECONDS];
        long last = -1;

        Ring() {
            Arrays.fill(values, UNKNOWN);
        }
    }

    /** Records the state of the integration at the second (epoch seconds) it was read. */
    void record(IntegrationInfo info, long second) {
        if (info.pid == null || (info.healthReady == null && info.healthLive == null)) {
            return;
        }
        // Camel reports readiness and liveness together; if only one of them were known, the other would be
        // recorded as down, as a second holds one value for both
        byte value = (byte) ((Boolean.TRUE.equals(info.healthReady) ? READY : 0)
                             | (Boolean.TRUE.equals(info.healthLive) ? LIVE : 0));
        Ring ring = rings.computeIfAbsent(info.pid, k -> new Ring());
        synchronized (ring) {
            if (ring.last >= 0 && second > ring.last) {
                // the seconds in between were not read: not known
                for (long s = ring.last + 1; s < second && s <= ring.last + SECONDS; s++) {
                    ring.values[(int) (s % SECONDS)] = UNKNOWN;
                }
            }
            if (second >= ring.last) {
                ring.values[(int) (second % SECONDS)] = value;
                ring.last = second;
            }
        }
    }

    /** The values of the seconds that end with the second given, oldest first, UNKNOWN where not known. */
    byte[] strip(String pid, long end, int seconds) {
        byte[] out = new byte[seconds];
        Arrays.fill(out, UNKNOWN);
        Ring ring = pid != null ? rings.get(pid) : null;
        if (ring == null) {
            return out;
        }
        synchronized (ring) {
            for (int i = 0; i < seconds; i++) {
                long s = end - seconds + 1 + i;
                if (s <= ring.last && s > ring.last - SECONDS) {
                    out[i] = ring.values[(int) (s % SECONDS)];
                }
            }
        }
        return out;
    }

    void remove(String pid) {
        rings.remove(pid);
    }
}
