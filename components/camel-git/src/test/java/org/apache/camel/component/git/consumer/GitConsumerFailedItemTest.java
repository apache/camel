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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.git.GitTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A commit, tag or branch whose exchange failed is reported to the exception handler and delivered again on the next
 * poll, and it is marked consumed once the route accepts it. The scheduler is not started: the test polls itself.
 */
public class GitConsumerFailedItemTest extends GitTestSupport {

    private final RecordingExceptionHandler exceptionHandler = new RecordingExceptionHandler();
    private final List<String> attempts = new CopyOnWriteArrayList<>();
    private final AtomicBoolean failing = new AtomicBoolean(true);

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

    private void assertFailedItemIsDeliveredAgain(Callable<Integer> poll, String item) throws Exception {
        MockEndpoint done = getMockEndpoint("mock:done");

        // the route fails: the failure is reported, and the item is offered again on the next poll
        assertEquals(1, poll.call());
        assertEquals(1, poll.call());
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

    private void attempt(Exchange exchange) {
        attempts.add(exchange.getMessage().getBody(String.class));
        if (failing.get()) {
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
            }
        };
    }
}
