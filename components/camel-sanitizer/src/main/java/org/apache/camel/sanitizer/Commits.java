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

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.UnitOfWork;

/**
 * The commits of consumers that rule R2 follows: where each one is (the unit of work that holds it, or the exchange
 * that holds it while it has no unit of work, such as a copy in a seda queue), and how often it ran. It keeps no
 * exchange.
 * <p/>
 * A commit is an on completion that an exchange holds when it gets a unit of work created for it (it moves into the
 * unit of work then). The unit of work of an exchange is the one it is given first (the routing engine creates a unit
 * of work for an exchange and gives it to that exchange at once; an exchange that shares it, such as a part of a split,
 * is given it later). There is one record per add, as one {@link Synchronization} can be added to many exchanges, or
 * twice to one; a record holds its on completion weakly, by identity.
 * <p/>
 * The places are a weak map of the units of work (an entry goes when its unit of work is gone, so nothing is removed by
 * hand and no exchange is kept: the place holds the id of the exchange it was created for), and a map of the exchanges
 * that hold commits without a unit of work, by exchange id. That map and the commits taken out to be run by someone
 * else are bounded ({@link #MAX_FOLLOWED}): past it the oldest is no longer followed, and counted. A unit of work that
 * is done keeps its records for as long as it lives, so a commit that it runs again is seen.
 * <p/>
 * All methods take the lock of this table, which is held briefly and never while calling out of the sanitizer (the
 * suppliers of stacks are the sanitizer's). The events of one thread come in the order of the code; events of different
 * threads in any order, but Camel hands over to a copy before it publishes the copy (such as before it queues it), so
 * the events of a copy come after the handover to it.
 */
final class Commits {

    /**
     * The most commits followed in the exchanges that hold them without a unit of work, and taken out; a safety net,
     * not a sampling limit.
     */
    static final int MAX_FOLLOWED = 10_000;

    /**
     * The most units of work that are done kept by the id of their exchange, for a second run of a commit that comes
     * after the exchange let go of its unit of work (such as a second done of the unit of work).
     */
    static final int MAX_DONE = 1000;

    /**
     * The most commits taken out that ran kept for a second run (not part of {@link #MAX_FOLLOWED}).
     */
    static final int MAX_RAN = 1000;

    // guarded by this
    private final Map<UnitOfWork, Place> unitsOfWork = new WeakHashMap<>();
    // the exchanges that hold commits without a unit of work, by exchange id, the oldest first
    private final Map<String, Place> exchanges = new LinkedHashMap<>();
    // the commits taken out to be run by someone else (handoverCompletions without a target), the oldest first
    private final ArrayDeque<Commit> taken = new ArrayDeque<>();
    // the last commits taken out that ran, for a second run (not counted in the bound), the oldest first
    private final ArrayDeque<Commit> ran = new ArrayDeque<>();
    // how many commits in taken and ran have an on completion of this identity hash, so a run of an on completion that
    // is not one of them does not look through them
    private final Map<Integer, Integer> takenIndex = new HashMap<>();
    // the last units of work that are done, by the id of the exchange they were created for, the oldest first
    private final Map<String, Place> done = new LinkedHashMap<>(64, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Place> eldest) {
            return size() > MAX_DONE;
        }
    };
    private int followed;
    // read without the lock to log once that the bound was reached
    private volatile long dropped;
    private long commits;

    /**
     * An exchange is given a unit of work, and the on completions it held move into it.
     *
     * @param exchangeId the exchange
     * @param routeId    the route the exchange came from (for a report), or null
     * @param endpoint   the URI of the endpoint the exchange came from (for a report), or null
     */
    synchronized void unitOfWork(
            String exchangeId, String routeId, String endpoint, UnitOfWork unitOfWork, List<Synchronization> moved) {
        Place place = unitOfWorkPlace(unitOfWork);
        if (place.owner == null) {
            // the first exchange given it is the one it was created for
            place.owner = exchangeId;
        }
        Place held = exchanges.remove(exchangeId);
        if (held != null) {
            followed -= held.commits.size();
        }
        boolean owner = exchangeId.equals(place.owner);
        for (Synchronization s : moved) {
            Commit c = held != null ? take(held.commits, s) : null;
            if (c != null) {
                // a commit handed over to this exchange moves into its unit of work
                place.add(c);
            } else if (owner) {
                commits++;
                place.add(new Commit(s, exchangeId, routeId, endpoint));
            }
        }
        if (held != null) {
            // the list of an exchange moves whole, so nothing should be left
            for (Commit c : held.commits) {
                c.place = null;
            }
        }
    }

    /**
     * An on completion is added to a unit of work: it is one of Camel, which reports its adds and its runs.
     */
    synchronized void added(UnitOfWork unitOfWork) {
        Place place = unitsOfWork.get(unitOfWork);
        if (place != null) {
            place.reportsRuns = true;
        }
    }

    /**
     * An on completion is removed from a unit of work, so it does not run.
     */
    synchronized void removed(UnitOfWork unitOfWork, Synchronization s) {
        Place place = unitsOfWork.get(unitOfWork);
        if (place != null) {
            Commit c = take(place.commits, s);
            if (c != null) {
                c.place = null;
            }
        }
    }

    /**
     * On completions are handed over from one exchange (from its unit of work, or from itself when it has none) to
     * another (into its unit of work, or itself when it has none).
     *
     * @param  step the step that runs on this thread (for a report), or null
     * @return      what moved and the violations found, for the stack of this thread to be added outside the lock (null
     *              if no commit moved and nothing was found)
     */
    synchronized Handover handover(
            String fromId, UnitOfWork fromUnitOfWork, String toId, UnitOfWork toUnitOfWork, List<Synchronization> moved,
            SanitizerStepListener.StepCall step) {
        Place from = fromUnitOfWork != null ? unitsOfWork.get(fromUnitOfWork) : exchanges.get(fromId);
        if (from == null || moved == null) {
            // no commit there; or a unit of work of a user that does not tell what moved (its commits are no longer
            // known to be there, but it reports no run either, so none is reported as never run)
            return null;
        }
        Handover result = null;
        Place to = null;
        for (Synchronization s : moved) {
            Commit c = take(from.commits, s);
            if (c == null) {
                Commit gone = from.handedOver != null ? find(from.handedOver, s) : null;
                if (gone != null && gone.place != null) {
                    // it left this unit of work by an earlier handover: it is handed over twice
                    result = result != null ? result : new Handover(fromId);
                    result.found.add(new Finding(Violation.COMMIT_HANDED_OVER_TWICE, gone, fromId, toId, step));
                }
                continue;
            }
            result = result != null ? result : new Handover(fromId);
            if (from.unitOfWork && from.owner != null && !from.owner.equals(fromId)) {
                result.found.add(new Finding(Violation.COMMIT_HANDED_OVER_BY_OTHER_EXCHANGE, c, fromId, toId, step));
            }
            if (to == null) {
                to = toUnitOfWork != null ? unitOfWorkPlace(toUnitOfWork) : exchangePlace(toId);
            }
            if (from.unitOfWork) {
                c.handedOverBy = fromId;
                // the stack is added outside the lock (handedOver)
                c.handover = null;
                result.fromUnitOfWork.add(c);
                if (from.handedOver == null) {
                    from.handedOver = new ArrayList<>(2);
                }
                from.handedOver.add(c);
            } else {
                followed--;
            }
            if (to.handedOver != null) {
                // back where it was handed over from (such as from a copy that seda did not queue)
                to.handedOver.remove(c);
            }
            to.add(c);
            if (!to.unitOfWork) {
                follow();
            }
        }
        if (!from.unitOfWork && from.commits.isEmpty()) {
            exchanges.remove(fromId, from);
        }
        return result;
    }

    /**
     * Adds the stack of the thread that handed over to the commits that left a unit of work, for a report of a second
     * handover (a second handover that comes before this shows no stack of the first).
     */
    synchronized void handedOver(Handover handover, Sanitizer.Trace trace) {
        for (Commit c : handover.fromUnitOfWork) {
            // not when another handover of it came in between (it shows its own exchange)
            if (c.handover == null && handover.fromId.equals(c.handedOverBy)) {
                c.handover = trace;
            }
        }
    }

    /**
     * On completions are taken out of an exchange (out of the exchange itself: what it holds without a unit of work),
     * to be run by someone else: they are followed for running once only.
     */
    synchronized void taken(String fromId, List<Synchronization> list) {
        Place from = exchanges.get(fromId);
        if (from == null) {
            return;
        }
        for (Synchronization s : list) {
            Commit c = take(from.commits, s);
            if (c != null) {
                // it left the exchange (followed) for the commits taken out (followed too)
                c.place = Place.TAKEN;
                taken.add(c);
                takenIndex.merge(c.hash, 1, Integer::sum);
            }
        }
        if (from.commits.isEmpty()) {
            exchanges.remove(fromId, from);
        }
    }

    /**
     * An on completion is about to run, with an exchange and its unit of work.
     *
     * @return the run of a commit, for the stack of this thread to be added outside the lock; null if it is not a
     *         commit
     */
    synchronized Run run(String exchangeId, UnitOfWork unitOfWork, Synchronization s) {
        Place place = unitOfWork != null ? unitsOfWork.get(unitOfWork) : null;
        if (place == null) {
            // the exchange let go of its unit of work when it was done: a unit of work run again after that
            place = done.get(exchangeId);
        }
        Commit c = place != null ? find(place.commits, s) : null;
        if (c == null && place != null && place.handedOver != null) {
            // it was handed over from this unit of work, but runs here: it runs again where it was handed over to
            c = find(place.handedOver, s);
        }
        if (c == null && takenIndex.containsKey(System.identityHashCode(s))) {
            // taken out (or ran after it was taken out): matched by the on completion only, as nothing tells which
            // exchange runs it; an on completion shared by many exchanges can match the record of another exchange
            c = find(taken, s);
            if (c != null) {
                // taken out and run once: no longer followed, but kept a while for a second run
                taken.remove(c);
                followed--;
                ran.add(c);
                if (ran.size() > MAX_RAN) {
                    Commit gone = ran.removeFirst();
                    gone.place = null;
                    unindex(gone);
                }
            } else {
                c = find(ran, s);
            }
        }
        if (c == null) {
            return null;
        }
        c.runs++;
        if (c.runs == 1) {
            c.firstRunWith = exchangeId;
            return new Run(c, null);
        }
        return new Run(c, new Finding(Violation.COMMIT_RUN_TWICE, c, exchangeId, null, null));
    }

    /**
     * Adds the stack of the first run of a commit (a second run that comes before this shows no stack of the first).
     */
    synchronized void ranFirst(Commit c, Sanitizer.Trace trace) {
        if (c.firstRun == null) {
            c.firstRun = trace;
        }
    }

    /**
     * An exchange is done: if its unit of work was created for it, a commit still in it that has not run never runs.
     *
     * @return the violations found (empty if none)
     */
    synchronized List<Finding> done(String exchangeId, String routeId, UnitOfWork unitOfWork) {
        Place place = unitOfWork != null ? unitsOfWork.get(unitOfWork) : null;
        if (place == null || place.done || !exchangeId.equals(place.owner)) {
            return List.of();
        }
        place.done = true;
        done.put(exchangeId, place);
        if (!place.reportsRuns) {
            // a unit of work of a user, which may run its on completions without telling
            return List.of();
        }
        List<Finding> found = null;
        for (Commit c : place.commits) {
            if (c.runs == 0) {
                Finding f = new Finding(Violation.COMMIT_NEVER_RUN, c, exchangeId, null, null);
                if (f.routeId == null) {
                    // a unit of work created before the exchange entered a route (such as by a producer template)
                    f.routeId = routeId;
                }
                found = add(found, f);
            }
        }
        // the records stay while the unit of work lives, so a second run is seen
        return found != null ? found : List.of();
    }

    /**
     * The CamelContext stopped: the commits still in an exchange that was never routed never run. The exchanges and the
     * commits taken out are no longer followed.
     *
     * @return the violations found (empty if none)
     */
    synchronized List<Finding> stopped() {
        List<Finding> found = null;
        for (Map.Entry<String, Place> e : exchanges.entrySet()) {
            for (Commit c : e.getValue().commits) {
                if (c.runs == 0) {
                    found = add(found, new Finding(Violation.COMMIT_NEVER_RUN, c, e.getKey(), null, null));
                }
                c.place = null;
            }
        }
        exchanges.clear();
        taken.clear();
        ran.clear();
        takenIndex.clear();
        done.clear();
        followed = 0;
        return found != null ? found : List.of();
    }

    /**
     * How many commits were followed since this table was created.
     */
    synchronized long getCommitCount() {
        return commits;
    }

    /**
     * How many commits are no longer followed because of the bound.
     */
    long getDropped() {
        return dropped;
    }

    synchronized int getFollowed() {
        return followed;
    }

    private Place unitOfWorkPlace(UnitOfWork unitOfWork) {
        return unitsOfWork.computeIfAbsent(unitOfWork, u -> new Place(true, null));
    }

    private Place exchangePlace(String exchangeId) {
        return exchanges.computeIfAbsent(exchangeId, id -> new Place(false, id));
    }

    /**
     * One more commit is followed in an exchange or taken out: past the bound the oldest is no longer followed.
     */
    private void follow() {
        followed++;
        if (followed > MAX_FOLLOWED) {
            if (!taken.isEmpty()) {
                // first the oldest taken out (checked for running once only)
                Commit gone = taken.removeFirst();
                gone.place = null;
                unindex(gone);
            } else {
                Iterator<Place> it = exchanges.values().iterator();
                Place oldest = it.next();
                oldest.commits.remove(0).place = null;
                if (oldest.commits.isEmpty()) {
                    it.remove();
                }
            }
            followed--;
            dropped++;
        }
    }

    /**
     * The commit of this on completion in the list, one that has not run first; or null.
     */
    private static Commit find(Iterable<Commit> list, Synchronization s) {
        Commit ran = null;
        for (Commit c : list) {
            if (c.sync.get() == s) {
                if (c.runs == 0) {
                    return c;
                }
                ran = ran != null ? ran : c;
            }
        }
        return ran;
    }

    /**
     * Takes the commit of this on completion out of the list, one that has not run first; or null.
     */
    private static Commit take(List<Commit> list, Synchronization s) {
        Commit c = find(list, s);
        if (c != null) {
            list.remove(c);
        }
        return c;
    }

    /**
     * What a handover moved out of a unit of work, and the violations it found.
     */
    static final class Handover {
        final String fromId;
        final List<Commit> fromUnitOfWork = new ArrayList<>(2);
        final List<Finding> found = new ArrayList<>(1);

        Handover(String fromId) {
            this.fromId = fromId;
        }
    }

    /**
     * The run of a commit, and the violation it found (a second run) or null.
     */
    record Run(Commit commit, Finding found) {
    }

    private void unindex(Commit c) {
        takenIndex.computeIfPresent(c.hash, (k, n) -> n > 1 ? n - 1 : null);
    }

    private static List<Finding> add(List<Finding> found, Finding f) {
        List<Finding> answer = found != null ? found : new ArrayList<>(2);
        answer.add(f);
        return answer;
    }

    /**
     * Where commits are: a unit of work, or an exchange that has none.
     */
    static final class Place {

        // the place of the commits taken out to be run by someone else
        static final Place TAKEN = new Place(false, null);

        final boolean unitOfWork;
        // a unit of work: the exchange it was created for (null until known); an exchange: its id
        String owner;
        // a unit of work of Camel, which reports its adds and its runs
        boolean reportsRuns;
        boolean done;
        final List<Commit> commits = new ArrayList<>(2);
        // a unit of work: the commits that left it by a handover
        List<Commit> handedOver;

        Place(boolean unitOfWork, String owner) {
            this.unitOfWork = unitOfWork;
            this.owner = owner;
        }

        void add(Commit c) {
            c.place = this;
            commits.add(c);
        }
    }

    /**
     * The record of one add of a commit. It holds the on completion weakly, and ids and names.
     */
    static final class Commit {

        final WeakReference<Synchronization> sync;
        final int hash;
        final String type;
        // the exchange it was added for, the route and the endpoint it came from
        final String exchangeId;
        final String routeId;
        final String endpoint;
        // where it is; null when it is no longer followed
        Place place;
        int runs;
        Sanitizer.Trace firstRun;
        String firstRunWith;
        // the last handover from a unit of work
        Sanitizer.Trace handover;
        String handedOverBy;

        Commit(Synchronization s, String exchangeId, String routeId, String endpoint) {
            this.sync = new WeakReference<>(s);
            this.hash = System.identityHashCode(s);
            this.type = s.getClass().getName();
            this.exchangeId = exchangeId;
            this.routeId = routeId;
            this.endpoint = endpoint;
        }
    }

    /**
     * A violation found, with what its report needs, taken under the lock.
     */
    static final class Finding {

        final Violation kind;
        final String type;
        final String exchangeId;
        // the route the exchange of the commit came from (set when it is done, if not known when the commit was added)
        String routeId;
        final String endpoint;
        // the exchange of the event, and the one it handed over to
        final String with;
        final String to;
        final String stepRouteId;
        final String stepId;
        // the stack of the event, added outside the lock
        Sanitizer.Trace trace;
        final Sanitizer.Trace firstRun;
        final String firstRunWith;
        final Sanitizer.Trace handover;
        final String handedOverBy;
        final boolean copy;

        Finding(Violation kind, Commit c, String with, String to, SanitizerStepListener.StepCall step) {
            this.kind = kind;
            this.type = c.type;
            this.exchangeId = c.exchangeId;
            this.routeId = c.routeId;
            this.endpoint = c.endpoint;
            this.with = with;
            this.to = to;
            // the step that runs on the thread, when it is a step of the exchange that handed over or of the exchange
            // the commit was added for (such as a processor that sends a copy of it); a step of another exchange, such
            // as of a route that sent this one, is not where it happened
            boolean ofExchange = step != null
                    && (step.getExchangeId().equals(with) || step.getExchangeId().equals(c.exchangeId));
            this.stepRouteId = ofExchange ? step.getRouteId() : null;
            this.stepId = ofExchange ? step.getNodeId() : null;
            this.firstRun = c.firstRun;
            this.firstRunWith = c.firstRunWith;
            this.handover = c.handover;
            this.handedOverBy = c.handedOverBy;
            this.copy = c.place != null && !c.place.unitOfWork && c.place != Place.TAKEN;
        }
    }

    /**
     * The ids of the exchanges that hold commits without a unit of work (for tests).
     */
    synchronized Set<String> heldByExchanges() {
        return new LinkedHashSet<>(exchanges.keySet());
    }
}
