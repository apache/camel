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

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.git.GitTestSupport;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.ScheduledPollConsumerScheduler;
import org.apache.camel.support.service.ServiceSupport;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The scheduler may run the first poll as soon as the consumer starts it (initialDelay=0), so the consumer must have
 * opened the repository before it starts the scheduler. The scheduler of this test runs the first poll right away, in
 * the thread that starts the consumer.
 */
public class GitConsumerFirstPollTest extends GitTestSupport {

    private final RecordingExceptionHandler exceptionHandler = new RecordingExceptionHandler();

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("recordingExceptionHandler", exceptionHandler);
        registry.bind("commitScheduler", new PollOnStartScheduler());
        registry.bind("tagScheduler", new PollOnStartScheduler());
        registry.bind("branchScheduler", new PollOnStartScheduler());
    }

    @Test
    public void firstPollFindsTheRepository() throws Exception {
        // the first polls ran while the consumers were started, one route after the other
        assertEquals(List.of(), exceptionHandler.getExceptions(), "the first polls do not fail");
        List<Object> bodies = getMockEndpoint("mock:done").getReceivedExchanges().stream()
                .map(exchange -> exchange.getMessage().getBody())
                .toList();
        assertEquals(List.of(commitMessage, "refs/tags/" + tagTest, "refs/heads/master"), bodies);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        // the repository has a commit, a tag and a branch before the consumers start
        try (Git git = getGitTestRepository()) {
            new File(getGitDir(), filenameToAdd).createNewFile();
            git.add().addFilepattern(filenameToAdd).call();
            git.commit().setMessage(commitMessage).call();
            git.tag().setName(tagTest).call();
        }
        final String uri = "git://" + getGitDir().getPath() + "?exceptionHandler=#recordingExceptionHandler&type=";

        return new RouteBuilder() {
            @Override
            public void configure() {
                from(uri + "commit&branchName=master&scheduler=#commitScheduler").to("mock:done");
                from(uri + "tag&scheduler=#tagScheduler").to("mock:done");
                from(uri + "branch&scheduler=#branchScheduler").to("mock:done");
            }
        };
    }

    /**
     * Runs the task once when the scheduler is started.
     */
    private static final class PollOnStartScheduler extends ServiceSupport implements ScheduledPollConsumerScheduler {

        private CamelContext camelContext;
        private Runnable task;
        private boolean started;

        @Override
        public void onInit(Consumer consumer) {
            // noop
        }

        @Override
        public void scheduleTask(Runnable task) {
            this.task = task;
        }

        @Override
        public void unscheduleTask() {
            task = null;
        }

        @Override
        public void startScheduler() {
            if (!started && task != null) {
                started = true;
                task.run();
            }
        }

        @Override
        public boolean isSchedulerStarted() {
            return started;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }
    }
}
