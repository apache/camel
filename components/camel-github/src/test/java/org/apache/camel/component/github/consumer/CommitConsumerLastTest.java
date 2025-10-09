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
package org.apache.camel.component.github.consumer;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.github.GitHubComponentTestBase;
import org.apache.camel.component.github.GitHubConstants;
import org.apache.camel.support.DefaultScheduledPollConsumerScheduler;
import org.junit.jupiter.api.Test;

public class CommitConsumerLastTest extends GitHubComponentTestBase {

    @BindToRegistry("myScheduler")
    private final MyScheduler scheduler = createScheduler();

    private MyScheduler createScheduler() {
        MyScheduler scheduler = new MyScheduler();
        scheduler.setDelay(100);
        scheduler.setInitialDelay(0);
        return scheduler;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("github://commit/master?startingSha=last&repoOwner=anotherguy&repoName=somerepo&scheduler=#myScheduler")
                        .routeId("foo").noAutoStartup()
                        .process(new GitHubCommitProcessor())
                        .to(mockResultEndpoint);
            }
        };
    }

    @Test
    public void commitConsumerLongHistoryLastShaTest() throws Exception {
        for (int i = 0; i < 2000; i++) {
            commitService.addRepositoryCommit("existing commit " + i);
        }

        mockResultEndpoint.setAssertPeriod(500);
        mockResultEndpoint.expectedBodiesReceived("new commit 1", "new commit 2");

        context.getRouteController().startAllRoutes();

        commitService.addRepositoryCommit("new commit 1");
        commitService.addRepositoryCommit("new commit 2");

        mockResultEndpoint.assertIsSatisfied();
    }

    public class GitHubCommitProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            String author = exchange.getMessage().getHeader(GitHubConstants.GITHUB_COMMIT_AUTHOR, String.class);
            String sha = exchange.getMessage().getHeader(GitHubConstants.GITHUB_COMMIT_SHA, String.class);
            if (log.isDebugEnabled()) {
                log.debug("Commit SHA: {}", sha);
                log.debug("Got commit with author: {}: SHA {}", author, sha);
            }
        }
    }

    private static final class MyScheduler extends DefaultScheduledPollConsumerScheduler {

        @Override
        public void startScheduler() {
            super.startScheduler();
            try {
                /*
                    adding a delay to the CommitConsumer.doStart() method to force the CommitConsumer.poll()
                    method to be called before the CommitConsumer.doStart() finishes which could leave the
                    lastSha variable null
                 */
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
