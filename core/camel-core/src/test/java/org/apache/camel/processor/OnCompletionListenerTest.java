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
package org.apache.camel.processor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.OnCompletionListener;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.SynchronizationVetoable;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.UnitOfWorkHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link OnCompletionListener} sees each move, add, handover, take and run of an on completion, on the paths that
 * Camel moves them today: a commit handed over to a copy sent to seda, the copy that seda does not queue giving it back
 * (CAMEL-25123), and the parts of a split with shareUnitOfWork, which hand over only their own on completions
 * (CAMEL-26576). With no listener, nothing changes.
 */
class OnCompletionListenerTest extends ContextTestSupport {

    private static final long WAIT_SECONDS = 10;

    private final Recorder recorder = new Recorder();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("seda:queue");
                from("seda:queue").to("mock:queue");

                // no consumer, so the first message fills the queue and the next one is discarded
                from("direct:full").to("seda:full?size=1&discardWhenFull=true");

                from("direct:share")
                        .split(body().tokenize(",")).shareUnitOfWork()
                            .process(e -> e.getExchangeExtension().addOnCompletion((Commit) e.getProperty("own")))
                            .to("seda:parts")
                        .end();
                from("seda:parts").to("mock:parts");

                from("direct:remove")
                        .process(e -> e.getUnitOfWork().addSynchronization((Commit) e.getProperty("removed")))
                        .process(e -> e.getUnitOfWork().removeSynchronization((Commit) e.getProperty("removed")))
                        // it is no longer there, so this one is not reported
                        .process(e -> e.getUnitOfWork().removeSynchronization((Commit) e.getProperty("removed")));
            }
        };
    }

    @Test
    void testHandoverToACopySentToSeda() throws Exception {
        context.getCamelContextExtension().addOnCompletionListener(recorder);
        Commit commit = new Commit("commit");

        Exchange exchange = createExchangeWithBody("A");
        exchange.getExchangeExtension().addOnCompletion(commit);
        template.send("direct:start", exchange);
        commit.awaitRun();

        assertEquals(List.of(
                "unitOfWork e1 u1 [commit]",
                "added u1 commit",
                "handover e1 u1 -> e2 [commit]",
                "unitOfWork e2 u2 [commit]",
                "added u2 commit",
                "run e2 commit complete"),
                recorder.lines(commit));
        assertSame(exchange, recorder.events.peek().exchange);
        assertEquals(1, commit.runs.get());
    }

    @Test
    void testCopyNotQueuedGivesItBack() throws Exception {
        context.getCamelContextExtension().addOnCompletionListener(recorder);
        Commit queued = new Commit("queued");
        Commit discarded = new Commit("discarded");

        Exchange first = createExchangeWithBody("A");
        first.getExchangeExtension().addOnCompletion(queued);
        template.send("direct:full", first);
        Exchange second = createExchangeWithBody("B");
        second.getExchangeExtension().addOnCompletion(discarded);
        template.send("direct:full", second);
        discarded.awaitRun();

        // the copy has no unit of work, so it gives the commit back from its own list, and the exchange runs it
        assertEquals(List.of(
                "unitOfWork e1 u1 [discarded]",
                "added u1 discarded",
                "handover e1 u1 -> e2 [discarded]",
                "added u1 discarded",
                "handover e2 - -> e1 [discarded]",
                "run e1 discarded complete"),
                recorder.lines(discarded));
        assertEquals(1, discarded.runs.get());
        // the queued copy holds the other commit, which has not run
        assertEquals(List.of(
                "unitOfWork e1 u1 [queued]",
                "added u1 queued",
                "handover e1 u1 -> e2 [queued]"),
                recorder.lines(queued));
        assertEquals(0, queued.runs.get());
    }

    @Test
    void testSharedUnitOfWorkHandsOverOnlyThePartsOwn() throws Exception {
        context.getCamelContextExtension().addOnCompletionListener(recorder);
        Commit commit = new Commit("commit");
        Commit own = new Commit("own");

        Exchange exchange = createExchangeWithBody("A");
        exchange.setProperty("own", own);
        exchange.getExchangeExtension().addOnCompletion(commit);
        template.send("direct:share", exchange);
        commit.awaitRun();
        own.awaitRun();

        // the commit of the parent stays in the shared unit of work, and runs when the parent is done
        assertEquals(List.of(
                "unitOfWork e1 u1 [commit]",
                "added u1 commit",
                "run e1 commit complete"),
                recorder.lines(commit));
        // the part hands over the on completion it added, which runs with the copy
        assertEquals(List.of(
                "added u1 own",
                "handover e1 u1 -> e2 [own]",
                "unitOfWork e2 u2 [own]",
                "added u2 own",
                "run e2 own complete"),
                recorder.lines(own));
        // the part was given the unit of work of the parent
        Event part = recorder.events.stream()
                .filter(e -> e.type == Type.UNIT_OF_WORK && e.exchange != exchange
                        && e.unitOfWork == recorder.events.peek().unitOfWork)
                .findFirst().orElseThrow();
        assertTrue(part.list.isEmpty());
        assertEquals(1, commit.runs.get());
        assertEquals(1, own.runs.get());
    }

    @Test
    void testVetoedHandoverIsNotReported() throws Exception {
        context.getCamelContextExtension().addOnCompletionListener(recorder);
        Commit commit = new VetoedCommit("vetoed");

        Exchange exchange = createExchangeWithBody("A");
        exchange.getExchangeExtension().addOnCompletion(commit);
        template.send("direct:start", exchange);
        commit.awaitRun();

        // it refuses the handover to the copy sent to seda, so it stays and runs with the exchange
        assertEquals(List.of(
                "unitOfWork e1 u1 [vetoed]",
                "added u1 vetoed",
                "run e1 vetoed complete"),
                recorder.lines(commit));
        assertEquals(1, commit.runs.get());
        // nothing moved, so there is no handover event (and never one with an empty list)
        assertTrue(recorder.events.stream().noneMatch(e -> e.type == Type.HANDOVER && e.exchange == exchange));
        assertTrue(recorder.events.stream().noneMatch(e -> e.list != null && e.list.isEmpty() && e.type != Type.UNIT_OF_WORK));
    }

    @Test
    void testRemoved() {
        context.getCamelContextExtension().addOnCompletionListener(recorder);
        Commit removed = new Commit("removed");

        Exchange exchange = createExchangeWithBody("A");
        exchange.setProperty("removed", removed);
        template.send("direct:remove", exchange);

        assertEquals(List.of(
                "added u1 removed",
                "removed u1 removed"),
                recorder.lines(removed));
        assertEquals(0, removed.runs.get());
    }

    @Test
    void testTakenAndRunBySomeoneElse() {
        context.getCamelContextExtension().addOnCompletionListener(recorder);
        Commit commit = new Commit("commit");

        Exchange exchange = createExchangeWithBody("A");
        exchange.getExchangeExtension().addOnCompletion(commit);
        // as the aggregator, the enrichers and onCompletion do with an exchange that has no unit of work
        List<Synchronization> taken = exchange.getExchangeExtension().handoverCompletions();
        exchange.setException(new IllegalStateException("Forced"));
        UnitOfWorkHelper.doneSynchronizations(exchange, taken);

        assertEquals(List.of(
                "taken e1 [commit]",
                "run e1 commit failure"),
                recorder.lines(commit));
        assertEquals(1, commit.runs.get());
        // the listener gets a view of the list, which it cannot change
        Event event = recorder.events.peek();
        assertThrows(UnsupportedOperationException.class, () -> event.list.clear());
        assertEquals(1, taken.size());
    }

    @Test
    void testNoListener() throws Exception {
        assertTrue(context.getCamelContextExtension().getOnCompletionListeners().isEmpty());
        Commit commit = new Commit("commit");

        Exchange exchange = createExchangeWithBody("A");
        exchange.getExchangeExtension().addOnCompletion(commit);
        template.send("direct:start", exchange);
        commit.awaitRun();

        assertEquals(1, commit.runs.get());
    }

    @Test
    void testRemovedListenerIsNotCalled() throws Exception {
        context.getCamelContextExtension().addOnCompletionListener(recorder);
        // an equal listener is added once
        context.getCamelContextExtension().addOnCompletionListener(recorder);
        assertEquals(List.of(recorder), context.getCamelContextExtension().getOnCompletionListeners());
        context.getCamelContextExtension().removeOnCompletionListener(recorder);
        assertTrue(context.getCamelContextExtension().getOnCompletionListeners().isEmpty());

        Commit commit = new Commit("commit");
        Exchange exchange = createExchangeWithBody("A");
        exchange.getExchangeExtension().addOnCompletion(commit);
        template.send("direct:start", exchange);
        commit.awaitRun();

        assertTrue(recorder.events.isEmpty());
        assertEquals(1, commit.runs.get());
    }

    @Test
    void testFailingListenerDoesNotChangeTheRouting() throws Exception {
        OnCompletionListener failing = new OnCompletionListener() {
            @Override
            public void onUnitOfWork(Exchange exchange, UnitOfWork unitOfWork, List<Synchronization> moved) {
                throw new IllegalStateException("Forced");
            }

            @Override
            public void onCompletionAdded(UnitOfWork unitOfWork, Synchronization onCompletion) {
                throw new IllegalStateException("Forced");
            }

            @Override
            public void onCompletionHandover(
                    Exchange from, UnitOfWork fromUnitOfWork, Exchange to, List<Synchronization> moved) {
                throw new IllegalStateException("Forced");
            }

            @Override
            public void onCompletionRemoved(UnitOfWork unitOfWork, Synchronization onCompletion) {
                throw new IllegalStateException("Forced");
            }

            @Override
            public void onCompletionTaken(Exchange from, List<Synchronization> taken) {
                throw new IllegalStateException("Forced");
            }

            @Override
            public void onCompletionRun(Exchange exchange, Synchronization onCompletion, boolean failure) {
                throw new IllegalStateException("Forced");
            }
        };
        context.getCamelContextExtension().addOnCompletionListener(failing);
        context.getCamelContextExtension().addOnCompletionListener(recorder);
        Commit commit = new Commit("commit");

        Exchange exchange = createExchangeWithBody("A");
        exchange.getExchangeExtension().addOnCompletion(commit);
        template.send("direct:start", exchange);
        commit.awaitRun();

        assertFalse(exchange.isFailed());
        assertEquals(1, commit.runs.get());
        assertEquals(6, recorder.lines(commit).size());

        // also when it fails on a removal and on a take
        Commit removed = new Commit("removed");
        Exchange other = createExchangeWithBody("B");
        other.setProperty("removed", removed);
        template.send("direct:remove", other);
        assertFalse(other.isFailed());
        assertEquals(List.of("added u1 removed", "removed u1 removed"), recorder.lines(removed));

        Commit taken = new Commit("taken");
        Exchange third = createExchangeWithBody("C");
        third.getExchangeExtension().addOnCompletion(taken);
        assertEquals(List.of(taken), third.getExchangeExtension().handoverCompletions());
        assertEquals(List.of("taken e1 [taken]"), recorder.lines(taken));
    }

    @Test
    void testUnitOfWorkThatDoesNotTellWhatMoved() {
        List<String> calls = new ArrayList<>();
        Exchange target = createExchangeWithBody("B");
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            calls.add(method.getName() + " " + args.length);
            return null;
        };
        UnitOfWork custom = (UnitOfWork) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { UnitOfWork.class }, handler);

        // a custom unit of work that does not implement the new method hands over as before, and tells nothing
        assertNull(custom.handoverSynchronizationWithResult(target, null));
        assertNull(custom.handoverSynchronizationWithResult(target, s -> true));
        assertEquals(List.of("handoverSynchronization 1", "handoverSynchronization 2"), calls);
    }

    enum Type {
        UNIT_OF_WORK,
        ADDED,
        REMOVED,
        HANDOVER,
        TAKEN,
        RUN
    }

    record Event(Type type, Exchange exchange, UnitOfWork unitOfWork, Exchange to, List<Synchronization> list,
            Synchronization onCompletion, boolean failure) {
    }

    /**
     * An on completion that counts its runs.
     */
    static class Commit implements Synchronization {
        private final String name;
        final AtomicInteger runs = new AtomicInteger();
        private final CountDownLatch run = new CountDownLatch(1);

        Commit(String name) {
            this.name = name;
        }

        @Override
        public void onComplete(Exchange exchange) {
            runs.incrementAndGet();
            run.countDown();
        }

        @Override
        public void onFailure(Exchange exchange) {
            runs.incrementAndGet();
            run.countDown();
        }

        void awaitRun() throws InterruptedException {
            assertTrue(run.await(WAIT_SECONDS, TimeUnit.SECONDS), name + " did not run");
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * An on completion that refuses to be handed over.
     */
    static final class VetoedCommit extends Commit implements SynchronizationVetoable {
        VetoedCommit(String name) {
            super(name);
        }

        @Override
        public boolean allowHandover() {
            return false;
        }

        @Override
        public void beforeHandover(Exchange target) {
            // not handed over
        }
    }

    /**
     * Records the events, and renders those of one on completion as lines, with the exchanges and the units of work
     * named in the order they first appear in those lines.
     */
    static final class Recorder implements OnCompletionListener {
        final Queue<Event> events = new ConcurrentLinkedQueue<>();

        @Override
        public void onUnitOfWork(Exchange exchange, UnitOfWork unitOfWork, List<Synchronization> moved) {
            events.add(new Event(Type.UNIT_OF_WORK, exchange, unitOfWork, null, moved, null, false));
        }

        @Override
        public void onCompletionAdded(UnitOfWork unitOfWork, Synchronization onCompletion) {
            events.add(new Event(Type.ADDED, null, unitOfWork, null, null, onCompletion, false));
        }

        @Override
        public void onCompletionRemoved(UnitOfWork unitOfWork, Synchronization onCompletion) {
            events.add(new Event(Type.REMOVED, null, unitOfWork, null, null, onCompletion, false));
        }

        @Override
        public void onCompletionHandover(
                Exchange from, UnitOfWork fromUnitOfWork, Exchange to, List<Synchronization> moved) {
            events.add(new Event(Type.HANDOVER, from, fromUnitOfWork, to, moved, null, false));
        }

        @Override
        public void onCompletionTaken(Exchange from, List<Synchronization> taken) {
            events.add(new Event(Type.TAKEN, from, null, null, taken, null, false));
        }

        @Override
        public void onCompletionRun(Exchange exchange, Synchronization onCompletion, boolean failure) {
            events.add(new Event(Type.RUN, exchange, null, null, null, onCompletion, failure));
        }

        List<String> lines(Synchronization onCompletion) {
            Predicate<Event> about = e -> e.onCompletion == onCompletion
                    || e.list != null && e.list.stream().anyMatch(s -> s == onCompletion);
            Map<Object, String> names = new IdentityHashMap<>();
            List<String> lines = new ArrayList<>();
            for (Event e : events) {
                if (!about.test(e)) {
                    continue;
                }
                String line = switch (e.type) {
                    case UNIT_OF_WORK -> "unitOfWork " + name(names, e.exchange, "e") + " " + name(names, e.unitOfWork, "u")
                                         + " " + e.list;
                    case ADDED -> "added " + name(names, e.unitOfWork, "u") + " " + e.onCompletion;
                    case REMOVED -> "removed " + name(names, e.unitOfWork, "u") + " " + e.onCompletion;
                    case HANDOVER -> "handover " + name(names, e.exchange, "e") + " " + name(names, e.unitOfWork, "u")
                                     + " -> " + name(names, e.to, "e") + " " + e.list;
                    case TAKEN -> "taken " + name(names, e.exchange, "e") + " " + e.list;
                    case RUN -> "run " + name(names, e.exchange, "e") + " " + e.onCompletion + " "
                                + (e.failure ? "failure" : "complete");
                };
                lines.add(line);
            }
            return lines;
        }

        private static String name(Map<Object, String> names, Object o, String prefix) {
            if (o == null) {
                return "-";
            }
            return names.computeIfAbsent(o, k -> prefix + (names.keySet().stream()
                    .filter(n -> names.get(n).startsWith(prefix)).count() + 1));
        }
    }
}
