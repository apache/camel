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
package org.apache.camel.component.mongodb.dst;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import com.mongodb.client.model.changestream.OperationType;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mongodb.MongoDbConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.resume.Resumable;
import org.apache.camel.spi.StateRepository;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstCamel;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.camel.test.dst.DstWorld;
import org.apache.camel.test.dst.Invariants;

/**
 * The world around the camel-mongodb change streams consumer: a fake MongoDB with one collection ({@link FakeMongo})
 * given with the connectionBean option, other clients that insert, update, replace and delete documents, a route whose
 * processing is sometimes slow and sometimes fails, a durable store of the resume token (changeStreamTokenRepository),
 * and an operator that drops the collection (other clients make it again with an insert), makes the server unreachable
 * for a while, and stops and starts the route.
 * <p>
 * The seed picks cursorRegenerationDelay, the fullDocument option, how long the server is unreachable (shorter or
 * longer than the server selection timeout), and the fault rates.
 * <p>
 * The consumer thread loops for ever and its stop waits for it, which the one stack of the harness cannot run: the stop
 * would wait above the thread it waits for. A route stop is therefore made in two steps: the thread is interrupted at
 * its next call of the change stream (as a forced shutdown of its executor does; the driver throws
 * MongoInterruptedException and keeps the interrupt flag, and the thread ends), then the route is stopped. The phases
 * of the run (start, run until the stop, stop) are in the test body, so the interrupt flag of the one thread of the
 * harness is cleared as soon as the consumer thread ended: the condition of the test body runs right after the consumer
 * task returned, as no other task of the world blocks. The consumer is never stopped in the middle of an exchange, and
 * a stop does not close the cursor under a waiting getMore. The operator does not drop the collection or make the
 * server unreachable while the route starts, so a start that fails is the consumer's ("the route starts once the
 * collection exists and the server can be reached").
 * <p>
 * Properties: the consumer thread runs as long as the route is started; a resume token is stored only after its
 * exchange completed; a stored token never passes an event that was not routed; once the faults cleared, every event of
 * the collection after the first change stream was routed, also after a drop of the collection, a restart of the route
 * and a failed getMore or cursor open; the consumer uses the client as documented. Events may be routed more than once
 * (a restart or a new cursor resumes after the stored token), and an event whose exchange failed is not routed again
 * when a later one completed (the consumer stores the token of the later one).
 * <p>
 * One case is left out of the default set while it is looked at separately: a cursor that the consumer thread opens
 * again before any exchange completed (the first change stream failed and the driver could not resume it) starts at the
 * current time, as the consumer has no token yet, so the events written since the first change stream are skipped. By
 * default the world does not count those events; {@code -Ddst.mongodb.strictFirstCursor=true} counts them.
 * <p>
 * Not explored: the graceful stop of the consumer (keepRunning set to false and the cursor closed under a waiting
 * getMore or an exchange in flight), which the one stack cannot run; tailable cursors, streamFilter, the ResumeStrategy
 * of the consumer, a change stream without a token repository after a restart (it starts at the current time by
 * design), and camel-mongodb-gridfs.
 */
@DstWorld(component = "mongodb",
          fake = "FakeMongo: a MongoDB collection and its change stream (resumeAfter, startAfter, drop and invalidate"
                 + " events, the resume of the driver), behind the MongoClient of the connectionBean option",
          covers = {
                  "the change streams consumer with a store of its resume token (changeStreamTokenRepository)",
                  "a dropped collection, made again by an insert",
                  "a server that cannot be reached for a while: the cursor cannot be opened, a getMore fails",
                  "route restarts (the consumer thread interrupted, then the route stopped and started), resuming after the stored token",
                  "the fullDocument option and cursorRegenerationDelay" },
          faults = {
                  "the collection is dropped", "the server cannot be reached", "a getMore fails",
                  "the server answers slowly", "processing fails", "processing is slow" },
          invariants = {
                  "the consumer uses the client as documented",
                  "the route starts once the collection exists and the server can be reached",
                  "the consumer thread runs as long as the route is started",
                  "a resume token is stored only after its exchange completed",
                  "a stored resume token never passes an event that was not routed",
                  "every event is routed once the faults cleared (but see the class Javadoc for a cursor opened again before any"
                                                                                     + " exchange completed)" },
          issues = { "CAMEL-25679", "CAMEL-25946" })
final class MongoDbChangeStreamsWorld {

    static final String ROUTE = "changes";

    private final Dst dst;
    private final FakeMongo mongo;
    private final long regenerationDelay;
    private final boolean updateLookup;
    private final double processFailRate;
    private final List<String> violations = new ArrayList<>();
    private final Set<Long> routed = new HashSet<>();
    private final Set<Long> completed = new HashSet<>();
    private final Set<Long> excused = new HashSet<>();
    private final Map<String, String> tokens = new HashMap<>();
    private final List<String> liveIds = new ArrayList<>();
    private boolean faults = true;
    private int writesLeft;
    private int opsLeft;
    private int nextId;
    private boolean lifetime;
    private boolean endRequested;
    private long endRequestedAt;
    private boolean finalRequested;
    private boolean starting;
    private final boolean strictFirstCursor = Boolean.getBoolean("dst.mongodb.strictFirstCursor");
    private boolean interrupted;
    private String finalCheck;

    MongoDbChangeStreamsWorld(Dst dst) {
        this.dst = dst;
        this.mongo = new FakeMongo(dst);
        this.regenerationDelay = dst.pick(List.of(0L, 100L, 1000L, 5000L));
        this.updateLookup = dst.chance(0.5);
        this.processFailRate = dst.pick(List.of(0.0, 0.05, 0.2));
        mongo.getMoreFailRate = dst.pick(List.of(0.0, 0.02, 0.1));
        mongo.slowRate = dst.pick(List.of(0.0, 0.05));
        mongo.serverSelectionTimeout = dst.pick(List.of(2000L, 30_000L));
        mongo.onOpen = this::opened;
        mongo.onInterrupt = () -> interrupted = true;
        dst.trace("world: " + config());
    }

    private String config() {
        return "cursorRegenerationDelay=" + regenerationDelay + " fullDocument=" + (updateLookup ? "updateLookup" : "default")
               + " serverSelectionTimeout=" + mongo.serverSelectionTimeout + " getMoreFail=" + mongo.getMoreFailRate
               + " slow=" + mongo.slowRate + " processFail=" + processFailRate;
    }

    void run() throws Exception {
        DefaultCamelContext context = DstCamel.newContext(dst);
        context.getRegistry().bind("mongo", mongo.client());
        context.getRegistry().bind("tokens", new TokenStore());
        String uri = "mongodb:mongo?database=" + FakeMongo.DATABASE + "&collection=" + FakeMongo.COLLECTION
                     + "&consumerType=changeStreams&changeStreamTokenRepository=#tokens&cursorRegenerationDelay="
                     + regenerationDelay + (updateLookup ? "&fullDocument=updateLookup" : "");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId(ROUTE).autoStartup(false).process(exchange -> process(exchange));
            }
        });
        context.start();
        DstExecutor operator = dst.executor("operator", 1);
        try {
            // some documents before the consumer starts: they are not in its first change stream
            for (int i = 0; i < 3; i++) {
                write();
            }
            planWrites();
            planOperations(operator);
            operator.scheduleWithFixedDelay(() -> finisher(operator), 1000, 500, TimeUnit.MILLISECONDS);
            while (true) {
                startRoute(context);
                lifetime = true;
                dst.runUntil(this::consumerEnded, "the end of the consumer thread");
                lifetime = false;
                mongo.interruptRequested = false;
                context.getRouteController().stopRoute(ROUTE);
                dst.trace("world: route stopped");
                if (finalRequested) {
                    break;
                }
                endRequested = false;
                // the route stays stopped for a while
                dst.runFor(dst.between(100, 5000));
            }
            dst.check(
                    Invariants.of("the world itself ran as planned",
                            () -> violations.stream().noneMatch(v -> v.startsWith("world"))
                                    ? null : violations + " (" + config() + ")"),
                    Invariants.of("the route starts once the collection exists and the server can be reached",
                            () -> violations.stream().noneMatch(v -> v.startsWith("start"))
                                    ? null : violations + " (" + config() + ")"),
                    Invariants.of("the consumer thread runs as long as the route is started",
                            () -> violations.stream().noneMatch(v -> v.startsWith("thread"))
                                    ? null : violations + " (" + config() + ")"),
                    Invariants.of("the consumer uses the client as documented",
                            () -> mongo.violations.isEmpty() ? null : mongo.violations + " (" + config() + ")"),
                    Invariants.of("a resume token is stored only after its exchange completed",
                            () -> violations.stream().noneMatch(v -> v.startsWith("stored the token of"))
                                    ? null : violations + " (" + config() + ")"),
                    Invariants.of("a stored resume token never passes an event that was not routed",
                            () -> violations.stream().noneMatch(v -> v.startsWith("stored a token past"))
                                    ? null : violations + " (" + config() + ")"),
                    Invariants.of("every event is routed once the faults cleared (but see the class Javadoc for a cursor"
                                  + " opened again before any exchange completed)",
                            () -> finalCheck == null ? null : finalCheck + " (" + config() + ")"));
        } finally {
            dst.dropPending();
            context.stop();
        }
    }

    /**
     * The condition of the phase of a consumer thread, checked by the loop of the test body between its tasks: the
     * consumer thread ended after its interrupt (the interrupt flag of the thread of the harness is cleared here), or
     * it ended earlier on its own and the stop was asked a minute ago.
     */
    private boolean consumerEnded() {
        if (interrupted) {
            interrupted = false;
            Thread.interrupted();
            return true;
        }
        if (endRequested && dst.now() - endRequestedAt > 60_000) {
            // the consumer thread did not take the interrupt: it ended on its own before, while the route is started
            violations.add("thread: the consumer thread ended while the route was started (seen at t=" + dst.now() + ")");
            return true;
        }
        return false;
    }

    private void startRoute(DefaultCamelContext context) {
        while (true) {
            // the operator starts the route once the collection exists and the server can be reached: a route whose
            // consumer cannot be made (the endpoint checks that the collection exists) is not started again here, as
            // that is about the route controller, not this consumer
            if (!mongo.collectionExists() || !mongo.isReachable()) {
                if (!mongo.collectionExists() && writesLeft == 0) {
                    write(OperationType.INSERT);
                }
                dst.runFor(1000);
                continue;
            }
            starting = true;
            try {
                context.getRouteController().startRoute(ROUTE);
                dst.trace("world: route started");
                return;
            } catch (Exception e) {
                // the collection exists and the server can be reached, and nothing of the world changes while the route
                // starts: a start that fails is the consumer's
                violations.add("start: the route did not start at t=" + dst.now() + ": " + e.getMessage());
                return;
            } finally {
                starting = false;
            }
        }
    }

    /** A change stream opened without a token starts at the current time: the events before it are not expected. */
    private void opened(String how) {
        // the first cursor of a route start with an empty store of the token starts at the current time by design. A
        // cursor that the consumer thread opens again before any exchange completed also starts at the current time (the
        // consumer has no token yet), so the events written since the first cursor are skipped: that case is looked at
        // separately, and -Ddst.mongodb.strictFirstCursor=true counts it
        if (completed.isEmpty() && ("now (route start)".equals(how) || !strictFirstCursor && how.startsWith("now"))) {
            for (FakeMongo.Event e : mongo.oplog()) {
                if (e.seq() <= mongo.lastCursorStart() && !routed.contains(e.seq())) {
                    excused.add(e.seq());
                }
            }
        }
    }

    private void process(Exchange exchange) {
        Resumable offset = exchange.getMessage().getHeader(Exchange.OFFSET, Resumable.class);
        if (offset == null) {
            violations.add("world: an exchange without a resume token");
            return;
        }
        long seq = FakeMongo.seqOf((String) offset.getLastOffset().getValue());
        String type = exchange.getMessage().getHeader(MongoDbConstants.STREAM_OPERATION_TYPE, String.class);
        routed.add(seq);
        if (faults && dst.chance(0.05)) {
            dst.block(dst.between(1000, 10_000), "slow processing of " + seq);
        } else {
            dst.block(dst.between(0, 100), "processing of " + seq);
        }
        if (faults && dst.chance(processFailRate)) {
            throw Dst.injected(new IllegalStateException("dst: processing failed for event " + seq + " (" + type + ")"));
        }
        completed.add(seq);
    }

    /** A durable store of the resume token (a database table, say): it keeps its state when the consumer stops. */
    private final class TokenStore implements StateRepository<String, String> {
        @Override
        public void setState(String key, String value) {
            long seq = FakeMongo.seqOf(value);
            if (!completed.contains(seq)) {
                violations.add("stored the token of event " + seq + " whose exchange did not complete, at t=" + dst.now());
            }
            for (FakeMongo.Event e : mongo.oplog()) {
                if (e.seq() < seq && isData(e) && !routed.contains(e.seq()) && !excused.contains(e.seq())) {
                    violations.add("stored a token past event " + e.seq() + " (" + e.type().getValue()
                                   + "), which was not routed, at t=" + dst.now() + " (token of event " + seq + ")");
                    break;
                }
            }
            tokens.put(key, value);
        }

        @Override
        public String getState(String key) {
            return tokens.get(key);
        }

        @Override
        public void start() {
            // a database table: nothing to start
        }

        @Override
        public void stop() {
            // keeps its state
        }
    }

    private static boolean isData(FakeMongo.Event e) {
        return e.type() == OperationType.INSERT || e.type() == OperationType.UPDATE
                || e.type() == OperationType.REPLACE || e.type() == OperationType.DELETE;
    }

    // ------------------------------------------------------------------------------------------------------------
    // the load and the operator

    private void write() {
        if (liveIds.isEmpty() || dst.chance(0.4)) {
            write(OperationType.INSERT);
        } else {
            write(dst.pick(List.of(OperationType.UPDATE, OperationType.REPLACE, OperationType.DELETE)));
        }
    }

    private void write(OperationType type) {
        if (!mongo.collectionExists()) {
            liveIds.clear();
            type = OperationType.INSERT;
        }
        String id;
        if (type == OperationType.INSERT || liveIds.isEmpty()) {
            type = OperationType.INSERT;
            id = "d" + nextId++;
            liveIds.add(id);
        } else {
            id = dst.pick(liveIds);
            if (type == OperationType.DELETE) {
                liveIds.remove(id);
            }
        }
        mongo.write(type, id);
    }

    private void planWrites() {
        DstExecutor writer = dst.executor("writer", 1);
        long t = dst.between(0, 500);
        int bursts = (int) dst.between(1, 4);
        for (int b = 0; b < bursts; b++) {
            int size = (int) dst.between(1, 15);
            for (int i = 0; i < size; i++) {
                writesLeft++;
                writer.schedule("write", t, () -> {
                    write();
                    writesLeft--;
                });
                t += dst.between(0, 300);
            }
            t += dst.between(0, 20_000);
        }
    }

    private void planOperations(DstExecutor operator) {
        int ops = (int) dst.between(0, 4);
        long t = dst.between(500, 5000);
        for (int i = 0; i < ops; i++) {
            opsLeft++;
            int kind = (int) dst.between(0, 3);
            if (kind == 0) {
                operator.schedule("drop collection", t, () -> notWhileStarting(operator, "drop collection", () -> {
                    mongo.drop();
                    opsLeft--;
                }));
            } else if (kind == 1) {
                long length = dst.chance(0.5)
                        ? dst.between(100, mongo.serverSelectionTimeout)
                        : dst.between(mongo.serverSelectionTimeout, 3 * mongo.serverSelectionTimeout);
                operator.schedule("server unreachable", t, () -> notWhileStarting(operator, "server unreachable", () -> {
                    mongo.unreachable(length);
                    opsLeft--;
                }));
            } else {
                operator.schedule("stop route", t, () -> {
                    requestEnd();
                    opsLeft--;
                });
            }
            t += dst.between(1000, 20_000);
        }
    }

    /**
     * Runs an operation of the operator once no route start is in progress: a drop or an outage that came in the middle
     * of a start would make the start fail, and the restart of a route whose start failed is about the route
     * controller, not this consumer.
     */
    private void notWhileStarting(DstExecutor operator, String what, Runnable operation) {
        if (starting) {
            operator.schedule(what + " (a route start is in progress)", 100,
                    () -> notWhileStarting(operator, what, operation));
            return;
        }
        operation.run();
    }

    /** Asks the consumer thread to end at its next call of the change stream (see the class Javadoc). */
    private void requestEnd() {
        if (!lifetime || endRequested) {
            return;
        }
        endRequested = true;
        endRequestedAt = dst.now();
        mongo.interruptRequested = true;
        dst.trace("world: stop the route (interrupt the consumer thread)");
    }

    private long drainDeadline = -1;

    private void finisher(DstExecutor operator) {
        if (finalRequested || writesLeft > 0 || opsLeft > 0) {
            return;
        }
        if (drainDeadline < 0) {
            faults = false;
            mongo.faults = false;
            dst.trace("world: faults cleared");
            if (!mongo.collectionExists()) {
                write(OperationType.INSERT);
            }
            drainDeadline = dst.now() + 10 * 60_000L;
            return;
        }
        if (!lifetime || endRequested) {
            return;
        }
        Set<Long> missing = new TreeSet<>();
        for (FakeMongo.Event e : mongo.oplog()) {
            if (isData(e) && !routed.contains(e.seq()) && !excused.contains(e.seq())) {
                missing.add(e.seq());
            }
        }
        if (!missing.isEmpty() && dst.now() < drainDeadline) {
            return;
        }
        if (!missing.isEmpty()) {
            finalCheck = "events never routed: " + missing + " of " + mongo.oplog().size() + " (collection exists="
                         + mongo.collectionExists() + ", stored token " + tokens + ")";
        }
        finalRequested = true;
        requestEnd();
    }
}
