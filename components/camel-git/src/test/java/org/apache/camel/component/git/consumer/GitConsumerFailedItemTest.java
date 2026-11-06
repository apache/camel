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
package org.apache.camel.component.git.consumer;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.git.GitTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.support.ScheduledPollConsumer;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A commit, tag or branch whose exchange failed is reported to the exception handler and delivered again on the next
 * poll, and it is marked consumed once the route accepts it. A poll counts only the items that it marked consumed, so a
 * failed item does not make a greedy consumer poll again at once, and it counts as an idle poll for the backoff. The
 * scheduler is not started: the test polls itself, or runs the scheduled task itself.
 */
public class GitConsumerFailedItemTest extends GitTestSupport {

    private final RecordingExceptionHandler exceptionHandler = new RecordingExceptionHandler();
    private final List<String> attempts = new CopyOnWriteArrayList<>();
    private final AtomicBoolean failing = new AtomicBoolean(true);
    private volatile int recoverAtAttempt = Integer.MAX_VALUE;

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("recordingExceptionHandler", exceptionHandler);
    }

    @Test
    public void failedCommitIsDeliveredAgain() throws Exception {
        try (Git git = getGitTestRepository()) {
            commitFile(git);
        }
        GitCommitConsumer consumer = (GitCommitConsumer) context.getRoute("commits").getConsumer();

        assertFailedItemIsDeliveredAgain(consumer::poll, commitMessage);
    }

    @Test
    public void failedTagIsDeliveredAgain() throws Exception {
        try (Git git = getGitTestRepository()) {
            commitFile(git);
            git.tag().setName(tagTest).call();
        }
        GitTagConsumer consumer = (GitTagConsumer) context.getRoute("tags").getConsumer();

        assertFailedItemIsDeliveredAgain(consumer::poll, "refs/tags/" + tagTest);
    }

    @Test
    public void failedBranchIsDeliveredAgain() throws Exception {
        try (Git git = getGitTestRepository()) {
            commitFile(git);
        }
        GitBranchConsumer consumer = (GitBranchConsumer) context.getRoute("branches").getConsumer();

        assertFailedItemIsDeliveredAgain(consumer::poll, "refs/heads/master");
    }

    @Test
    public void greedyConsumerDoesNotPollAgainAfterFailedCommit() throws Exception {
        try (Git git = getGitTestRepository()) {
            commitFile(git);
        }
        ScheduledPollConsumer consumer = (ScheduledPollConsumer) context.getRoute("greedyCommits").getConsumer();
        // the route recovers at the 10th attempt, so that a consumer that polls again at once stops there
        recoverAtAttempt = 10;

        // one run of the scheduler: the commit fails and nothing is consumed, so the consumer waits for the next run
        consumer.run();
        assertEquals(List.of(commitMessage), attempts);

        consumer.run();
        assertEquals(List.of(commitMessage, commitMessage), attempts);
    }

    @Test
    public void greedyConsumerPollsAgainWhileCommitsAreConsumed() throws Exception {
        failing.set(false);
        try (Git git = getGitTestRepository()) {
            commit(git, "first");
            commit(git, "second");
            commit(git, "third");
        }
        ScheduledBatchPollingConsumer consumer
                = (ScheduledBatchPollingConsumer) context.getRoute("greedyCommits").getConsumer();
        // not an option of the git endpoint: each poll then consumes one commit and leaves the older ones
        consumer.setMaxMessagesPerPoll(1);

        // the commits come from the newest, and the consumer polls again at once as long as a poll consumed one
        consumer.run();
        assertEquals(List.of("third", "second", "first"), attempts);
    }

    @Test
    public void failedCommitCountsAsIdlePollForTheBackoff() throws Exception {
        try (Git git = getGitTestRepository()) {
            commitFile(git);
        }
        ScheduledPollConsumer consumer = (ScheduledPollConsumer) context.getRoute("backoffCommits").getConsumer();

        // backoffIdleThreshold=1 and backoffMultiplier=3: after a poll that consumed nothing, 2 runs are skipped
        for (int run = 0; run < 7; run++) {
            consumer.run();
        }
        assertEquals(List.of(commitMessage, commitMessage, commitMessage), attempts);
    }

    @Test
    public void failedCommitIsNotFollowedByEmptyMessage() throws Exception {
        try (Git git = getGitTestRepository()) {
            commitFile(git);
        }
        ScheduledPollConsumer consumer = (ScheduledPollConsumer) context.getRoute("idleCommits").getConsumer();

        // the poll delivered the commit, even if the route failed: it is not idle
        consumer.run();
        assertEquals(List.of(commitMessage), attempts);

        // once the commit is consumed, the next poll finds nothing and sends an empty message
        failing.set(false);
        consumer.run();
        consumer.run();
        assertEquals(Arrays.asList(commitMessage, commitMessage, null), attempts);
    }

    private void assertFailedItemIsDeliveredAgain(Callable<Integer> poll, String item) throws Exception {
        MockEndpoint done = getMockEndpoint("mock:done");

        // the route fails: the failure is reported, and the item is offered again on the next poll. A poll counts
        // only the items that it marked consumed.
        assertEquals(0, poll.call());
        assertEquals(0, poll.call());
        assertEquals(List.of(item, item), attempts);
        assertEquals(List.of(item, item), exceptionHandler.getBodies());
        assertInstanceOf(IllegalStateException.class, exceptionHandler.getExceptions().get(0));
        assertInstanceOf(IllegalStateException.class, exceptionHandler.getExceptions().get(1));
        assertEquals(0, done.getReceivedCounter());

        // the route accepts it: the item is marked consumed and not offered again
        failing.set(false);
        assertEquals(1, poll.call());
        assertEquals(0, poll.call());
        assertEquals(List.of(item, item, item), attempts);
        assertEquals(2, exceptionHandler.getBodies().size());
        assertEquals(1, done.getReceivedCounter());
    }

    private void commitFile(Git git) throws Exception {
        new File(getGitDir(), filenameToAdd).createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        git.commit().setMessage(commitMessage).call();
    }

    private void commit(Git git, String message) throws Exception {
        git.commit().setMessage(message).setAllowEmpty(true).call();
    }

    private void attempt(Exchange exchange) {
        attempts.add(exchange.getMessage().getBody(String.class));
        if (failing.get() && attempts.size() < recoverAtAttempt) {
            throw new IllegalStateException("Simulated failure");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        // force create git repo before routes
        getTestRepository();
        final String uri = "git://" + getGitDir().getPath()
                           + "?startScheduler=false&exceptionHandler=#recordingExceptionHandler&type=";

        return new RouteBuilder() {
            @Override
            public void configure() {
                from(uri + "commit&branchName=master").routeId("commits").process(e -> attempt(e)).to("mock:done");
                from(uri + "tag").routeId("tags").process(e -> attempt(e)).to("mock:done");
                from(uri + "branch").routeId("branches").process(e -> attempt(e)).to("mock:done");
                from(uri + "commit&branchName=master&greedy=true").routeId("greedyCommits")
                        .process(e -> attempt(e)).to("mock:done");
                from(uri + "commit&branchName=master&backoffIdleThreshold=1&backoffMultiplier=3").routeId("backoffCommits")
                        .process(e -> attempt(e)).to("mock:done");
                from(uri + "commit&branchName=master&sendEmptyMessageWhenIdle=true").routeId("idleCommits")
                        .process(e -> attempt(e)).to("mock:done");
            }
        };
    }
}
