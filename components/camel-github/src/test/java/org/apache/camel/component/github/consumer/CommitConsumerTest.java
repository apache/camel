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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.github.GitHubComponentTestBase;
import org.apache.camel.component.github.GitHubConstants;
import org.eclipse.egit.github.core.RepositoryCommit;
import org.junit.jupiter.api.Test;

public class CommitConsumerTest extends GitHubComponentTestBase {
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("github://commit/master?repoOwner=anotherguy&repoName=somerepo")
                        .process(new GitHubCommitProcessor())
                        .to(mockResultEndpoint);
            }
        };
    }

    @Test
    public void commitConsumerTest() throws Exception {
        mockResultEndpoint.expectedMessageCount(2);
        RepositoryCommit commit1 = commitService.addRepositoryCommit("test-1");
        RepositoryCommit commit2 = commitService.addRepositoryCommit("test-2");
        mockResultEndpoint.expectedBodiesReceivedInAnyOrder(commit1.getCommit().getMessage(), commit2.getCommit().getMessage());

        mockResultEndpoint.assertIsSatisfied();
    }

    public class GitHubCommitProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            String author = exchange.getMessage().getHeader(GitHubConstants.GITHUB_COMMIT_AUTHOR, String.class);
            String sha = exchange.getMessage().getHeader(GitHubConstants.GITHUB_COMMIT_SHA, String.class);
            if (log.isDebugEnabled()) {
                log.debug("Got commit with author: {}: SHA {}", author, sha);
            }
        }
    }
}
