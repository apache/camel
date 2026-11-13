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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The records of rule R2 ({@link Commits}) on the events of the on completion listener, in the orders the threads of
 * Camel can give them: no report on the correct paths, and each violation reported where it happens.
 */
class SanitizerCommitsTest {

    private static final Supplier<Sanitizer.Trace> TRACE = () -> new Sanitizer.Trace("t", null);

    private final Commits commits = new Commits();
    private final Synchronization s = new SynchronizationAdapter();

    @Test
    void aCommitRunsOnceWhenItsExchangeIsDone() {
        UnitOfWork p = camelUnitOfWork("A", s);
        assertNull(run("A", p, s));
        assertEquals(List.of(), commits.done("A", "route", p));
        assertEquals(1, commits.getCommitCount());
    }

    @Test
    void aCommitHandedOverRunsOnceWithTheCopy() {
        UnitOfWork p = camelUnitOfWork("A", s);
        assertEquals(List.of(), handover("A", p, "C", null, List.of(s)));
        assertEquals(List.of(), commits.done("A", "route", p));
        // the copy is routed: it gets a unit of work, and the commit moves into it
        UnitOfWork u = camelUnitOfWork("C", s);
        assertNull(run("C", u, s));
        assertEquals(List.of(), commits.done("C", "route", u));
        assertEquals(1, commits.getCommitCount());
        assertEquals(0, commits.getFollowed());
    }

    @Test
    void aCopyThatIsNotQueuedGivesTheCommitBack() {
        UnitOfWork p = camelUnitOfWork("A", s);
        handover("A", p, "C", null, List.of(s));
        // seda did not queue the copy: it hands the commit back to the exchange (into its unit of work)
        assertEquals(List.of(), handover("C", null, "A", p, List.of(s)));
        assertNull(run("A", p, s));
        assertEquals(List.of(), commits.done("A", "route", p));
        assertEquals(0, commits.getFollowed());
    }

    @Test
    void theOnCompletionsOfAPartAreNotCommits() {
        UnitOfWork p = camelUnitOfWork("A");
        Synchronization own = new SynchronizationAdapter();
        // a part is given the unit of work of its parent later, with its own on completions
        commits.unitOfWork("part", null, null, p, List.of(own));
        assertEquals(List.of(), handover("part", p, "C", null, List.of(own)));
        assertEquals(0, commits.getCommitCount());
    }

    @Test
    void aCommitHandedOverByAnotherExchangeIsReported() {
        UnitOfWork p = camelUnitOfWork("A", s);
        List<Commits.Finding> found = handover("part", p, "C", null, List.of(s));
        assertEquals(1, found.size());
        assertEquals(Violation.COMMIT_HANDED_OVER_BY_OTHER_EXCHANGE, found.get(0).kind);
        assertEquals("A", found.get(0).exchangeId);
        assertEquals("part", found.get(0).with);
        assertEquals("C", found.get(0).to);
    }

    @Test
    void aCommitHandedOverTwiceIsReportedAtTheSecondHandover() {
        UnitOfWork p = camelUnitOfWork("A", s);
        assertEquals(List.of(), handover("A", p, "C1", null, List.of(s)));
        // a second handover from the unit of work it already left (two parts that overlapped)
        List<Commits.Finding> found = handover("A", p, "C2", null, List.of(s));
        assertEquals(1, found.size());
        assertEquals(Violation.COMMIT_HANDED_OVER_TWICE, found.get(0).kind);
        assertEquals("A", found.get(0).handedOverBy);
        assertEquals("C2", found.get(0).to);
    }

    @Test
    void aCommitRunWhereItWasHandedOverFromAndByTheCopyRunsTwice() {
        UnitOfWork p = camelUnitOfWork("A", s);
        handover("A", p, "C", null, List.of(s));
        // it stayed in the unit of work too, and runs there
        assertNull(run("A", p, s));
        UnitOfWork u = camelUnitOfWork("C", s);
        Commits.Finding f = run("C", u, s);
        assertEquals(Violation.COMMIT_RUN_TWICE, f.kind);
        assertEquals("A", f.firstRunWith);
        assertEquals("C", f.with);
    }

    @Test
    void aUnitOfWorkDoneTwiceRunsTheCommitTwice() {
        UnitOfWork p = camelUnitOfWork("A", s);
        assertNull(run("A", p, s));
        assertEquals(List.of(), commits.done("A", "route", p));
        // the second done comes after the exchange let go of its unit of work
        Commits.Finding f = run("A", null, s);
        assertEquals(Violation.COMMIT_RUN_TWICE, f.kind);
        assertEquals(List.of(), commits.done("A", "route", null));
    }

    @Test
    void oneOnCompletionAddedTwiceRunsTwice() {
        // each add is a commit of its own (as Synchronization says)
        UnitOfWork p = camelUnitOfWork("A", s, s);
        assertNull(run("A", p, s));
        assertNull(run("A", p, s));
        assertEquals(Violation.COMMIT_RUN_TWICE, run("A", p, s).kind);
    }

    @Test
    void aCommitThatDoesNotRunIsReportedWhenItsExchangeIsDone() {
        UnitOfWork p = camelUnitOfWork("A", s);
        List<Commits.Finding> found = commits.done("A", "route", p);
        assertEquals(1, found.size());
        assertEquals(Violation.COMMIT_NEVER_RUN, found.get(0).kind);
        // once only
        assertEquals(List.of(), commits.done("A", "route", p));
    }

    @Test
    void theDoneOfAPartIsNotTheDoneOfTheUnitOfWork() {
        UnitOfWork p = camelUnitOfWork("A", s);
        commits.unitOfWork("part", null, null, p, List.of());
        assertEquals(List.of(), commits.done("part", "route", p));
        assertNull(run("A", p, s));
        assertEquals(List.of(), commits.done("A", "route", p));
    }

    @Test
    void aRemovedCommitDoesNotRun() {
        UnitOfWork p = camelUnitOfWork("A", s);
        commits.removed(p, s);
        assertEquals(List.of(), commits.done("A", "route", p));
    }

    @Test
    void aUnitOfWorkThatDoesNotReportItsAddsIsNotCheckedForCommitsThatNeverRun() {
        // a unit of work of a user: it may run its on completions without telling
        UnitOfWork p = unitOfWork();
        commits.unitOfWork("A", null, null, p, List.of(s));
        assertEquals(List.of(), commits.done("A", "route", p));
        // nor does it tell what it hands over
        assertEquals(List.of(), handover("A", p, "C", null, null));
    }

    @Test
    void aCommitTakenOutIsCheckedForRunningOnce() {
        UnitOfWork p = camelUnitOfWork("A", s);
        handover("A", p, "C", null, List.of(s));
        // the copy is not routed itself: its commits are taken out and run by someone else (seda multipleConsumers)
        commits.taken("C", List.of(s));
        assertNull(run("C", null, s));
        assertEquals(Violation.COMMIT_RUN_TWICE, run("C", null, s).kind);
        assertEquals(List.of(), commits.stopped());
    }

    @Test
    void aCommitInACopyThatIsNeverRoutedIsReportedWhenTheContextStops() {
        UnitOfWork p = camelUnitOfWork("A", s);
        handover("A", p, "C", null, List.of(s));
        assertEquals(List.of(), commits.done("A", "route", p));
        assertEquals(1, commits.getFollowed());

        List<Commits.Finding> found = commits.stopped();
        assertEquals(1, found.size());
        assertEquals(Violation.COMMIT_NEVER_RUN, found.get(0).kind);
        assertTrue(found.get(0).copy);
        assertEquals("C", found.get(0).with);
        assertEquals(0, commits.getFollowed());
        assertTrue(commits.heldByExchanges().isEmpty());
    }

    @Test
    void theCommitsInExchangesWithoutAUnitOfWorkAreBounded() {
        UnitOfWork p = camelUnitOfWork("A");
        List<Synchronization> all = new ArrayList<>();
        for (int i = 0; i <= Commits.MAX_FOLLOWED; i++) {
            all.add(new SynchronizationAdapter());
        }
        commits.unitOfWork("A", null, null, p, all);
        for (int i = 0; i <= Commits.MAX_FOLLOWED; i++) {
            handover("A", p, "C" + i, null, List.of(all.get(i)));
        }
        assertEquals(Commits.MAX_FOLLOWED, commits.getFollowed());
        assertEquals(1, commits.getDropped());
        // the oldest is no longer followed, so it is not reported
        assertFalse(commits.heldByExchanges().contains("C0"));
        assertEquals(Commits.MAX_FOLLOWED, commits.stopped().size());
    }

    @Test
    void manyThreadsHandOverAndRunTheirOwnCommits() throws Exception {
        // stress: each thread follows its own exchange through a handover, the copy and the runs, all at once
        int threads = 8;
        int rounds = 2000;
        CyclicBarrier start = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int id = t;
                results.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    int found = 0;
                    for (int i = 0; i < rounds; i++) {
                        Synchronization c = new SynchronizationAdapter();
                        String a = "A" + id + "-" + i;
                        UnitOfWork p = camelUnitOfWork(a, c);
                        found += handover(a, p, a + "C", null, List.of(c)).size();
                        found += commits.done(a, "route", p).size();
                        UnitOfWork u = camelUnitOfWork(a + "C", c);
                        found += run(a + "C", u, c) != null ? 1 : 0;
                        found += commits.done(a + "C", "route", u).size();
                    }
                    return found;
                }));
            }
            for (Future<Integer> f : results) {
                assertEquals(0, f.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(threads * rounds, commits.getCommitCount());
        assertEquals(0, commits.getFollowed());
    }

    @Test
    void theCommitsTakenOutAreBoundedAndLetGoOnceTheyRan() {
        UnitOfWork p = camelUnitOfWork("A");
        List<Synchronization> all = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            all.add(new SynchronizationAdapter());
        }
        commits.unitOfWork("A", null, null, p, all);
        for (int i = 0; i < 3; i++) {
            handover("A", p, "C" + i, null, List.of(all.get(i)));
            commits.taken("C" + i, List.of(all.get(i)));
        }
        assertEquals(3, commits.getFollowed());
        // one that ran is no longer counted, and a second run of it is still seen
        assertNull(run("X", null, all.get(0)));
        assertEquals(2, commits.getFollowed());
        assertEquals(Violation.COMMIT_RUN_TWICE, run("X", null, all.get(0)).kind);
        // past the bound the oldest taken out goes first, before a commit that waits in an exchange
        Synchronization waiting = new SynchronizationAdapter();
        List<Synchronization> more = new ArrayList<>();
        for (int i = 0; i < Commits.MAX_FOLLOWED; i++) {
            more.add(new SynchronizationAdapter());
        }
        UnitOfWork q = camelUnitOfWork("Q", waiting);
        handover("Q", q, "W", null, List.of(waiting));
        UnitOfWork r = camelUnitOfWork("R");
        commits.unitOfWork("R", null, null, r, more);
        for (int i = 0; i < Commits.MAX_FOLLOWED - 1; i++) {
            handover("R", r, "D" + i, null, List.of(more.get(i)));
        }
        assertEquals(Commits.MAX_FOLLOWED, commits.getFollowed());
        assertEquals(2, commits.getDropped());
        assertTrue(commits.heldByExchanges().contains("W"));
    }

    private List<Commits.Finding> handover(
            String from, UnitOfWork fromUnitOfWork, String to, UnitOfWork toUnitOfWork, List<Synchronization> moved) {
        Commits.Handover h = commits.handover(from, fromUnitOfWork, to, toUnitOfWork, moved, null);
        if (h == null) {
            return List.of();
        }
        commits.handedOver(h, TRACE.get());
        return h.found;
    }

    private Commits.Finding run(String exchangeId, UnitOfWork unitOfWork, Synchronization s) {
        Commits.Run r = commits.run(exchangeId, unitOfWork, s);
        if (r == null) {
            return null;
        }
        if (r.found() == null) {
            commits.ranFirst(r.commit(), TRACE.get());
            return null;
        }
        return r.found();
    }

    /**
     * A unit of work of Camel created for the exchange, given to it with these on completions (which it reports as
     * added, as Camel's unit of work does).
     */
    private UnitOfWork camelUnitOfWork(String exchangeId, Synchronization... moved) {
        UnitOfWork u = unitOfWork();
        commits.unitOfWork(exchangeId, "route", "direct://start", u, List.of(moved));
        commits.added(u);
        return u;
    }

    private static UnitOfWork unitOfWork() {
        return (UnitOfWork) Proxy.newProxyInstance(UnitOfWork.class.getClassLoader(), new Class<?>[] { UnitOfWork.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "UnitOfWork@" + System.identityHashCode(proxy);
                    default -> null;
                });
    }
}
