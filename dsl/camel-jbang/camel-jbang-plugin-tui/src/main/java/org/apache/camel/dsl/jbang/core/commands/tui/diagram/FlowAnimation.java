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
package org.apache.camel.dsl.jbang.core.commands.tui.diagram;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.LongSupplier;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.StatInfo;

/**
 * The messages moving through a route diagram while it is on screen: how many a second reach each step, and the steps
 * where exchanges wait without any finishing.
 * <p>
 * The numbers come from the counts of the steps (or the routes of a topology), which the diagram loads again about once
 * a second: a reload brings new objects to read them from, and the change of the counts since the last one is the rate.
 * The frames in between (every {@link #FRAME_MS} ms) move the dots on the arrows, so the diagram shows where the
 * messages go without any more calls to the integration.
 */
public final class FlowAnimation {

    /** How often a frame is drawn while something moves: about 12 a second. */
    public static final long FRAME_MS = 80;
    /** With no new numbers for this long (the diagram is hidden, or the integration stopped) nothing moves. */
    static final long STALE_MS = 3000;
    /** Exchanges that wait at a step this long without one finishing make it a stuck step. */
    static final long STUCK_MS = 2000;
    /**
     * A new object with the same counts this soon after the last reading is the same reading laid out again (a zoom).
     */
    private static final long RELAID_MS = 500;
    /** A track not seen for this long (another route, a step gone after a reload) is forgotten. */
    private static final long FORGET_MS = 60_000;
    /** How much of the rate is from the readings before the last one. */
    private static final double SMOOTHING = 0.6;

    /**
     * The counts of a step or a route as the diagram has them now, from the object they were read from: the same object
     * again is no new reading.
     */
    public record Reading(Object source, long total, long failed, long inflight) {

        public static Reading of(StatInfo stat) {
            return new Reading(stat, stat.exchangesTotal, stat.exchangesFailed, stat.exchangesInflight);
        }
    }

    /** What the diagram knows of one step, or of the arrow into it. */
    public static final class Track {
        private Object last;
        private long total;
        private long failed;
        private long sampleAt;
        private long progressAt;
        private long seenAt;
        private double rate;
        private double failRate;
        private long inflight;
        private long stuckSince = -1;
        private double phase;

        /** Exchanges a second that reached the step, from the last two reloads. */
        public double rate() {
            return rate;
        }

        /** Exchanges a second that failed at the step. */
        public double failRate() {
            return failRate;
        }

        /** The exchanges at the step now. */
        public long inflight() {
            return inflight;
        }

        /** The distance the dots on the arrow have moved, in cells. */
        public double phase() {
            return phase;
        }
    }

    private final Map<String, Track> tracks = new HashMap<>();
    private final LongSupplier clock;
    private boolean enabled = true;
    private long lastUpdate = -1;

    public FlowAnimation() {
        this(() -> System.nanoTime() / 1_000_000);
    }

    FlowAnimation(LongSupplier clock) {
        this.clock = clock;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            tracks.clear();
            lastUpdate = -1;
        }
    }

    /**
     * Brings the tracks up to date with the counts of the steps on screen, by their key: counts from an object not seen
     * before are a new reading, from the same one again the frame between two reloads. Then the dots move on by the
     * time since the last frame.
     */
    public void update(Map<String, Reading> stats) {
        long now = clock.getAsLong();
        double dt = lastUpdate < 0 ? 0 : Math.min(0.25, (now - lastUpdate) / 1000.0);
        lastUpdate = now;
        for (Map.Entry<String, Reading> e : stats.entrySet()) {
            Track t = tracks.computeIfAbsent(e.getKey(), k -> new Track());
            t.seenAt = now;
            read(t, e.getValue(), now);
        }
        for (Iterator<Track> it = tracks.values().iterator(); it.hasNext();) {
            Track t = it.next();
            if (now - t.seenAt > FORGET_MS) {
                it.remove();
                continue;
            }
            t.phase += speed(t.rate) * dt;
        }
    }

    private static void read(Track t, Reading stat, long now) {
        if (stat.source() != t.last && t.last != null && now - t.sampleAt < RELAID_MS
                && stat.total() == t.total && stat.failed() == t.failed) {
            t.last = stat.source();
        }
        if (stat.source() == t.last) {
            if (now - t.sampleAt > STALE_MS) {
                // no new numbers: nothing is known to move
                t.rate = 0;
                t.failRate = 0;
            }
            return;
        }
        if (t.last != null && now > t.sampleAt) {
            double seconds = (now - t.sampleAt) / 1000.0;
            t.rate = smooth(t.rate, Math.max(0, stat.total() - t.total) / seconds);
            t.failRate = smooth(t.failRate, Math.max(0, stat.failed() - t.failed) / seconds);
        }
        boolean progressed = t.last == null || stat.total() > t.total;
        if (progressed) {
            t.progressAt = now;
        }
        t.inflight = stat.inflight();
        if (t.inflight > 0 && !progressed) {
            if (t.stuckSince < 0) {
                t.stuckSince = t.progressAt;
            }
        } else {
            t.stuckSince = -1;
        }
        t.last = stat.source();
        t.total = stat.total();
        t.failed = stat.failed();
        t.sampleAt = now;
    }

    /**
     * The rate with the readings before it: a few messages a second come in fits and starts, and the dots would jump
     * between fast and slow. No messages stops it at once, so the dots stop when the messages do.
     */
    private static double smooth(double rate, double reading) {
        return reading == 0 || rate == 0 ? reading : rate * SMOOTHING + reading * (1 - SMOOTHING);
    }

    /** The cells a second the dots move: faster the more messages there are. */
    static double speed(double rate) {
        return rate <= 0 ? 0 : 3 + 2.2 * log2(1 + rate);
    }

    /** The cells between two dots on an arrow: closer the more messages there are. */
    public static int spacing(double rate) {
        return (int) Math.max(4, Math.min(14, Math.round(14 - 2 * log2(1 + rate))));
    }

    private static double log2(double v) {
        return Math.log(v) / Math.log(2);
    }

    /** The track of a step or arrow, or null when it is not known. */
    public Track get(String key) {
        return tracks.get(key);
    }

    /** How long exchanges have waited at the step without one finishing, or -1 when it is not stuck. */
    public long stuckMillis(Track t) {
        if (t == null || t.stuckSince < 0 || t.inflight <= 0) {
            return -1;
        }
        long waited = clock.getAsLong() - t.stuckSince;
        return waited >= STUCK_MS ? waited : -1;
    }

    /** The time on the clock of the animation, for the frames of a spinner. */
    public long now() {
        return clock.getAsLong();
    }

    /** Something on screen moves: dots on an arrow, or the spinner of a stuck step. */
    public boolean isAnimating() {
        if (!enabled || lastUpdate < 0 || clock.getAsLong() - lastUpdate > STALE_MS) {
            return false;
        }
        for (Track t : tracks.values()) {
            if (t.rate > 0 || stuckMillis(t) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** The next frame of the animation is due: it is time to draw the diagram again. */
    public boolean frameDue() {
        return isAnimating() && clock.getAsLong() - lastUpdate >= FRAME_MS;
    }
}
