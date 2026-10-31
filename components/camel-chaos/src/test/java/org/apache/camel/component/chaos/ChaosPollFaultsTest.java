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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.chaos.TestPollComponent.TestPollConsumer;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.support.ScheduledPollConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The faults at the polls of a scheduled poll consumer, around the poll strategy of the consumer.
 */
class ChaosPollFaultsTest extends ChaosTestSupport {

    @BindToRegistry("testpoll")
    private final TestPollComponent component = new TestPollComponent();

    @BindToRegistry("recording")
    private final RecordingStrategy strategy = new RecordingStrategy();

    @BindToRegistry("retrying")
    private final RetryingStrategy retrying = new RetryingStrategy();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("testpoll:a?delay=5&initialDelay=0&pollStrategy=#recording").routeId("a").to("mock:a");
                from("testpoll:b?delay=5&initialDelay=0").routeId("b").to("mock:b");
                from("testpoll:c?delay=5&initialDelay=0&pollStrategy=#retrying").routeId("c").to("mock:c");
            }
        };
    }

    private TestPollConsumer consumer(String routeId) {
        return (TestPollConsumer) context.getRoute(routeId).getConsumer();
    }

    @Test
    void theStrategyOfTheConsumerIsWrappedOnce() {
        ScheduledPollConsumer a = consumer("a");
        assertThat(a.getPollStrategy()).isInstanceOf(ChaosPollStrategy.class);
        assertThat(((ChaosPollStrategy) a.getPollStrategy()).getDelegate()).isSameAs(strategy);
        // without its own strategy a consumer gets the default one inside the chaos one
        assertThat(consumer("b").getPollStrategy()).isInstanceOf(ChaosPollStrategy.class);
    }

    @Test
    void withoutRulesThePollsRunAsBefore() {
        await().atMost(10, TimeUnit.SECONDS).until(() -> strategy.commits.get() >= 3);
        int committed = strategy.commits.get();
        assertThat(strategy.begins.get()).isGreaterThanOrEqualTo(committed);
        assertThat(strategy.rollbacks).isEmpty();
    }

    @Test
    void dropSkipsThePollWithoutAskingTheStrategy() {
        TestPollConsumer a = consumer("a");
        String id = chaos().addRule("endpoint=testpoll:a* drop");
        // after the first dropped poll, no poll runs while the rule fires at every poll
        await().atMost(10, TimeUnit.SECONDS).until(() -> fired(id, "poll") >= 1);
        int polls = a.polls.get();
        int begins = strategy.begins.get();
        await().atMost(10, TimeUnit.SECONDS).until(() -> fired(id, "poll") >= 6);
        // a poll that began before the rule was added may still finish
        assertThat(a.polls.get()).isLessThanOrEqualTo(polls + 1);
        assertThat(strategy.begins.get()).isLessThanOrEqualTo(begins + 1);
        // the other consumer is not touched
        TestPollConsumer b = consumer("b");
        int bPolls = b.polls.get();
        await().atMost(10, TimeUnit.SECONDS).until(() -> b.polls.get() > bPolls);

        chaos().removeRule(id);
        int after = a.polls.get();
        await().atMost(10, TimeUnit.SECONDS).until(() -> a.polls.get() > after);
    }

    @Test
    void errorFailsThePollAfterTheStrategyBeganIt() {
        String id = chaos().addRule(
                "endpoint=testpoll:a* error exception=" + IOException.class.getName() + " message=\"poll down\" until=3");
        await().atMost(10, TimeUnit.SECONDS).until(() -> strategy.rollbacks.size() >= 3);
        assertThat(fired(id, "poll")).isEqualTo(3);
        // the strategy of the consumer began each poll and rolled it back with the exception of the rule (wrapped, as
        // the poll strategy declares no checked exception)
        for (Exception e : strategy.rollbacks.subList(0, 3)) {
            assertThat(e.getCause()).isInstanceOf(IOException.class).hasMessage("poll down");
        }
        // each failed poll was begun by the strategy of the consumer first
        int committed = strategy.commits.get();
        assertThat(strategy.begins.get()).isGreaterThanOrEqualTo(committed + 3);
        assertThat(ruleReport(id).getString("state")).isEqualTo("ended");
        // after the rule ended the polls commit again
        int commits = strategy.commits.get();
        await().atMost(10, TimeUnit.SECONDS).until(() -> strategy.commits.get() > commits);
    }

    @Test
    void theStrategyOfTheConsumerGetsTheRetryCounterOfAFailedPoll() {
        // a strategy that retries while the counter is below 2, as for a poll that failed by itself: attempts 0, 1, 2
        String id = chaos().addRule("endpoint=testpoll:c* error");
        await().atMost(10, TimeUnit.SECONDS).until(() -> retrying.counters.size() >= 6);
        // a copy: the consumer goes on polling
        assertThat(List.copyOf(retrying.counters).subList(0, 6)).containsExactly(0, 1, 2, 0, 1, 2);
        assertThat(fired(id, "poll")).isGreaterThanOrEqualTo(6);
    }

    @Test
    void dropAndErrorAtThePollsOfAFileConsumer(@TempDir Path dir) throws Exception {
        // the file stays where it is while the polls are dropped or fail, and is read once the rules ended
        Files.writeString(dir.resolve("order.txt"), "an order");
        String drop = chaos().addRule("component=file drop at=poll until=2");
        String error = chaos().addRule("component=file error at=poll until=2");
        getMockEndpoint("mock:files").expectedBodiesReceived("an order");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("file:" + dir + "?initialDelay=0&delay=10").routeId("files").convertBodyTo(String.class)
                        .to("mock:files");
            }
        });

        getMockEndpoint("mock:files").assertIsSatisfied();
        // the first two polls were dropped (the first rule decides), the next two failed, then the file was read
        assertThat(fired(drop, "poll")).isEqualTo(2);
        assertThat(fired(error, "poll")).isEqualTo(2);
        assertThat(ruleReport(error).getString("state")).isEqualTo("ended");
        await().atMost(10, TimeUnit.SECONDS).until(() -> !Files.exists(dir.resolve("order.txt")));
    }

    @Test
    void runtimeExceptionIsThrownAsIs() {
        chaos().addRule("endpoint=testpoll:a* error message=boom until=1");
        await().atMost(10, TimeUnit.SECONDS).until(() -> !strategy.rollbacks.isEmpty());
        assertThat(strategy.rollbacks.get(0)).isInstanceOf(ChaosException.class).hasMessage("boom");
    }

    @Test
    void anErrorClassIsWrappedAndTheConsumerGoesOnPolling() {
        TestPollConsumer a = consumer("a");
        chaos().addRule("endpoint=testpoll:a* error exception=" + AssertionError.class.getName() + " until=1");
        await().atMost(10, TimeUnit.SECONDS).until(() -> !strategy.rollbacks.isEmpty());
        assertThat(strategy.rollbacks.get(0)).isInstanceOf(RuntimeCamelException.class)
                .hasCauseInstanceOf(AssertionError.class);
        // the scheduled task was not cancelled: the consumer polls again
        int polls = a.polls.get();
        await().atMost(10, TimeUnit.SECONDS).until(() -> a.polls.get() > polls + 2);
    }

    @Test
    void aRestartedRouteHasANewConsumerThatIsWrappedToo() throws Exception {
        TestPollConsumer before = consumer("b");
        context.getRouteController().stopRoute("b");
        context.getRouteController().startRoute("b");
        TestPollConsumer after = consumer("b");
        assertThat(after).isNotSameAs(before);
        assertThat(after.getPollStrategy()).isInstanceOf(ChaosPollStrategy.class);

        String id = chaos().addRule("endpoint=testpoll:b* drop");
        await().atMost(10, TimeUnit.SECONDS).until(() -> fired(id, "poll") >= 2);
    }

    @Test
    void rulesAndCountsStayWhenARouteIsRemovedAndAddedAgain() throws Exception {
        String id = chaos().addRule("endpoint=testpoll:a* drop until=1000000");
        await().atMost(10, TimeUnit.SECONDS).until(() -> fired(id, "poll") >= 1);
        context.getRouteController().stopRoute("a");
        context.removeRoute("a");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("testpoll:a?delay=5&initialDelay=0&pollStrategy=#recording").routeId("a").to("mock:a");
            }
        });

        // the reloaded route gets a wrapped consumer, and the rule goes on counting
        assertThat(consumer("a").getPollStrategy()).isInstanceOf(ChaosPollStrategy.class);
        long fired = fired(id, "poll");
        await().atMost(10, TimeUnit.SECONDS).until(() -> fired(id, "poll") > fired);
        assertThat(ruleReport(id).getString("state")).isEqualTo("active");
    }

    @Test
    void atSendDoesNotFireAtPolls() {
        String id = chaos().addRule("endpoint=testpoll:a* drop at=send");
        int commits = strategy.commits.get();
        await().atMost(10, TimeUnit.SECONDS).until(() -> strategy.commits.get() >= commits + 3);
        assertThat(ruleReport(id).getLong("matches")).isZero();
    }

    @Test
    void polledMessagesStillFlowWhenTheRuleDoesNotFire() throws Exception {
        String id = chaos().addRule("endpoint=testpoll:nomatch* drop");
        getMockEndpoint("mock:a").expectedBodiesReceived("polled", "polled");
        consumer("a").messages.set(2);
        getMockEndpoint("mock:a").assertIsSatisfied();
        assertThat(ruleReport(id).getLong("matches")).isZero();
    }

    /** A poll strategy of the consumer that retries a failed poll while the retry counter is below 2. */
    static final class RetryingStrategy implements PollingConsumerPollStrategy {
        final List<Integer> counters = new CopyOnWriteArrayList<>();

        @Override
        public boolean begin(Consumer consumer, Endpoint endpoint) {
            return true;
        }

        @Override
        public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
            // nothing
        }

        @Override
        public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception cause) {
            counters.add(retryCounter);
            return retryCounter < 2;
        }
    }

    /** A poll strategy of the consumer, which records what it was asked. */
    static final class RecordingStrategy implements PollingConsumerPollStrategy {
        final AtomicInteger begins = new AtomicInteger();
        final AtomicInteger commits = new AtomicInteger();
        final List<Exception> rollbacks = new CopyOnWriteArrayList<>();

        @Override
        public boolean begin(Consumer consumer, Endpoint endpoint) {
            begins.incrementAndGet();
            return true;
        }

        @Override
        public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
            commits.incrementAndGet();
        }

        @Override
        public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception cause) {
            rollbacks.add(cause);
            return false;
        }
    }
}
