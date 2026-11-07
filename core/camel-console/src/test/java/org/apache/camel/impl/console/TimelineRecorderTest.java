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
package org.apache.camel.impl.console;

import java.util.List;
import java.util.Map;

import org.apache.camel.impl.console.TimelineRecorder.Counters;
import org.apache.camel.impl.console.TimelineRecorder.RouteSecond;
import org.apache.camel.impl.console.TimelineRecorder.Second;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimelineRecorderTest {

    // a whole second, plus a little: when the sampler runs
    private static final long T = 1_000_000_000L * 1000 + 5;

    private final TimelineRecorder recorder = new TimelineRecorder(60);

    private static Map<String, Counters> orders(long completed, long failed, long handled, long time) {
        return Map.of("orders", new Counters(completed, failed, handled, 2, time));
    }

    private RouteSecond orders(long second) {
        Second s = recorder.seconds(second, 1).get(0);
        return s != null ? s.routes.get("orders") : null;
    }

    private static long second(long millis) {
        return millis / 1000;
    }

    @Test
    void theRiseOfTheCountersIsWhatTheRouteDidInTheSecondBefore() {
        recorder.sample(T, orders(10, 1, 0, 100));
        recorder.sample(T + 1000, orders(15, 2, 1, 200));

        assertThat(recorder.endSecond(T + 1000)).isEqualTo(second(T));
        RouteSecond rs = orders(second(T));
        // 5 done, of which 1 handled; and 1 failed
        assertThat(rs.completed).isEqualTo(4);
        assertThat(rs.handled).isEqualTo(1);
        assertThat(rs.failed).isEqualTo(1);
        assertThat(rs.inflight).isEqualTo(2);
        assertThat(rs.mean()).isEqualTo(20);
    }

    @Test
    void aLateSampleSpreadsTheRiseOverItsSeconds() {
        recorder.sample(T, orders(0, 0, 0, 0));
        recorder.sample(T + 3000, orders(10, 0, 0, 0));

        assertThat(orders(second(T)).completed).isEqualTo(3);
        assertThat(orders(second(T) + 1).completed).isEqualTo(3);
        assertThat(orders(second(T) + 2).completed).isEqualTo(4);
        assertThat(TimelineRecorder.share(10, 3, 0) + TimelineRecorder.share(10, 3, 1) + TimelineRecorder.share(10, 3, 2))
                .isEqualTo(10);
    }

    @Test
    void aCounterThatWasResetIsNoRise() {
        recorder.sample(T, orders(10, 5, 0, 100));
        recorder.sample(T + 1000, orders(2, 0, 0, 10));
        recorder.sample(T + 2000, orders(5, 1, 0, 40));

        assertThat(orders(second(T)).completed).isZero();
        assertThat(orders(second(T)).failed).isZero();
        assertThat(orders(second(T) + 1).completed).isEqualTo(3);
        assertThat(orders(second(T) + 1).failed).isEqualTo(1);
    }

    @Test
    void anExchangeRedeliveredInARouteThatCompletesIsRecoveredThere() {
        recorder.sample(T, Map.of("orders", new Counters(0, 0, 0, 0, 0), "stock", new Counters(0, 0, 0, 0, 0)));
        recorder.exchangeCreated("A", "orders", T + 10);
        recorder.redelivery("A", "stock", T + 100);
        recorder.redelivery("A", "stock", T + 200);
        recorder.exchangeDone("A", "orders", "direct:orders", T + 300, 290, false, false, null, null, null, false);
        recorder.sample(T + 1000, Map.of("orders", new Counters(1, 0, 0, 0, 290), "stock", new Counters(1, 0, 0, 0, 280)));

        Second s = recorder.seconds(second(T), 1).get(0);
        // recovered where it was redelivered, not in the route that called it
        assertThat(s.routes.get("stock").recovered).isEqualTo(1);
        assertThat(s.routes.get("stock").completed).isZero();
        assertThat(s.routes.get("stock").attempts).isEqualTo(2);
        assertThat(s.routes.get("orders").recovered).isZero();
        assertThat(s.routes.get("orders").completed).isEqualTo(1);
        // kept with the failed and handled ones, as an exchange of the second worth a look
        assertThat(s.failed).singleElement().satisfies(k -> {
            assertThat(k.redelivered()).isTrue();
            assertThat(k.routeId()).isEqualTo("orders");
        });
        assertThat(s.slowest).isEmpty();
    }

    @Test
    void aRecoveredExchangeIsKeptWithTheFailureItWasRedeliveredFor() {
        recorder.exchangeCreated("A", "orders", T + 10);
        recorder.redelivery("A", "stock", T + 100, "Connection refused");
        recorder.redelivery("A", "stock", T + 200, "Read timed out");
        recorder.exchangeDone("A", "orders", "direct:orders", T + 300, 5, false, false, null, null, null, false);
        // a quick exchange that is not one of the slowest: the recovered one is kept all the same
        for (int i = 0; i < 5; i++) {
            recorder.exchangeDone("S" + i, "orders", "direct:orders", T + 400, 50, false, false, null, null, null, false);
        }

        Second s = recorder.seconds(second(T), 1).get(0);
        assertThat(s.failed).singleElement().satisfies(k -> {
            assertThat(k.exchangeId()).isEqualTo("A");
            assertThat(k.redelivered()).isTrue();
            assertThat(k.failed()).isFalse();
            assertThat(k.exception()).isEqualTo("Read timed out");
        });
        assertThat(s.slowest).extracting(TimelineRecorder.Kept::exchangeId).doesNotContain("A");
    }

    @Test
    void anExchangeThatIsHandledOrFailsAfterARedeliveryIsNotRecovered() {
        recorder.redelivery("A", "orders", T + 100);
        recorder.exchangeDone("A", "orders", null, T + 200, 10, false, true, null, null, null, false);
        recorder.redelivery("B", "orders", T + 100);
        recorder.exchangeDone("B", "orders", null, T + 200, 10, true, false, "boom", null, null, false);

        Second s = recorder.seconds(second(T), 1).get(0);
        assertThat(s.routes.get("orders").recovered).isZero();
        // both are kept with the failed ones, as the interesting exchanges of the second
        assertThat(s.failed).extracting(TimelineRecorder.Kept::exchangeId).containsExactly("A", "B");
        assertThat(s.failed.get(1).exception()).isEqualTo("boom");
        assertThat(s.slowest).isEmpty();
    }

    @Test
    void theFailedExchangesAndTheThreeSlowestAreKept() {
        for (int i = 0; i < 15; i++) {
            recorder.exchangeDone("F" + i, "orders", null, T + i, i, true, false, "boom", null, null, false);
        }
        for (int i = 0; i < 10; i++) {
            recorder.exchangeDone("S" + i, "orders", null, T + i, i * 10, false, false, null, null, null, false);
        }
        Second s = recorder.seconds(second(T), 1).get(0);

        assertThat(s.failed).hasSize(TimelineRecorder.KEEP_FAILED);
        assertThat(s.slowest).extracting(TimelineRecorder.Kept::exchangeId).containsExactly("S9", "S8", "S7");
        assertThat(s.done).isEqualTo(25);
    }

    @Test
    void aPartOfASplitIsNotKept() {
        recorder.exchangeDone("C", "orders", null, T, 10, false, false, null, null, "P", true);

        Second s = recorder.seconds(second(T), 1).get(0);
        assertThat(s.done).isZero();
        assertThat(s.slowest).isEmpty();
    }

    @Test
    void aCopyDoneInTheRouteOfItsExchangeIsPartOfItButACopyConsumedByARouteOfItsOwnIsNot() {
        recorder.exchangeCreated("P", "orders", T);
        recorder.exchangeSent("P", "orders", "http://stock", 7);
        // the copy of a circuit breaker, which calls pay
        recorder.exchangeCreated("CB", "orders", T + 5);
        recorder.exchangeSent("CB", "orders", "http://pay", 3);
        recorder.exchangeDone("CB", "orders", null, T + 10, 5, false, false, null, null, "P", false);
        // a copy of P sent to seda, consumed by the shipping route
        recorder.exchangeDone("S", "shipping", null, T + 20, 5, false, false, null, null, "P", false);
        // P itself, which the circuit breaker gave its own id as correlation id
        recorder.exchangeDone("P", "orders", null, T + 30, 30, false, false, null, null, "P", false);

        Second s = recorder.seconds(second(T), 1).get(0);
        assertThat(s.done).isEqualTo(2);
        assertThat(s.slowest).extracting(TimelineRecorder.Kept::exchangeId).containsExactly("P", "S");
        assertThat(s.slowest.get(0).sends()).extracting(TimelineRecorder.Send::endpointUri)
                .containsExactly("http://stock", "http://pay");
    }

    @Test
    void theP95AndMaxOfASecondAreTakenWhenItCloses() {
        recorder.sample(T - 1000, orders(0, 0, 0, 0));
        for (int i = 1; i <= 100; i++) {
            recorder.exchangeDone("E" + i, "orders", null, T - 900, i, false, false, null, null, null, false);
        }
        recorder.sample(T, orders(100, 0, 0, 5050));

        RouteSecond rs = orders(second(T) - 1);
        assertThat(rs.p95).isEqualTo(95);
        assertThat(rs.max).isEqualTo(100);
        assertThat(rs.elapsed).isNull();
    }

    @Test
    void theCallsOfAnExchangeAreKeptWithIt() {
        recorder.exchangeCreated("A", "orders", T);
        recorder.exchangeSent("A", "orders", "http://stock", 40);
        recorder.exchangeSent("A", "orders", "kafka:orders", 5);
        recorder.exchangeDone("A", "orders", null, T + 100, 100, false, false, null, null, null, false);

        assertThat(recorder.seconds(second(T), 1).get(0).slowest.get(0).sends())
                .containsExactly(new TimelineRecorder.Send("http://stock", 40), new TimelineRecorder.Send("kafka:orders", 5));
        assertThat(recorder.inflightCount()).isZero();
    }

    @Test
    void theErrorsAreCountedPerSecondByKind() {
        TimelineRecorder.ErrorKind kind = TimelineRecorder.errorKind("orders",
                new IllegalStateException("HTTP operation failed invoking http://stock with statusCode: 503"), "r1");
        recorder.error(kind, T);
        recorder.error(kind, T + 10);
        recorder.error(TimelineRecorder.errorKind("orders", new IllegalArgumentException("bad"), null), T + 1000);

        assertThat(kind.statusCode()).isEqualTo(503);
        assertThat(kind.exception()).isEqualTo("java.lang.IllegalStateException");
        List<Second> secs = recorder.seconds(second(T) + 1, 2);
        assertThat(secs.get(0).errors.get(kind)[0]).isEqualTo(2);
        assertThat(secs.get(1).errors).hasSize(1);
    }

    @Test
    void anExchangeThatDoesNotCompleteIsNoLongerFollowedAfterFiveMinutes() {
        recorder.exchangeCreated("A", "orders", T);
        recorder.sample(T + 4 * 60 * 1000, Map.of());
        assertThat(recorder.inflightCount()).isEqualTo(1);
        recorder.sample(T + 6 * 60 * 1000, Map.of());
        assertThat(recorder.inflightCount()).isZero();
    }

    @Test
    void theRingKeepsTheRecordedSecondsOnly() {
        recorder.exchangeDone("OLD", "orders", null, T, 1, false, false, null, null, null, false);
        recorder.exchangeDone("NEW", "orders", null, T + 61_000, 1, false, false, null, null, null, false);

        assertThat(recorder.seconds(second(T), 1).get(0)).isNull();
        assertThat(recorder.seconds(second(T) + 61, 1).get(0).slowest.get(0).exchangeId()).isEqualTo("NEW");
    }
}
