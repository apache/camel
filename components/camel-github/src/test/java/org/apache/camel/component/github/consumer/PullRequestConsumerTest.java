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
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.github.GitHubComponentTestBase;
import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.User;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PullRequestConsumerTest extends GitHubComponentTestBase {
    protected static final Logger LOG = LoggerFactory.getLogger(PullRequestConsumerTest.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("github://pullRequest?repoOwner=anotherguy&repoName=somerepo")
                        .process(new MockPullRequestProcessor())
                        .to(mockResultEndpoint);
            }
        };
    }

    @Test
    public void pullRequestTest() throws Exception {
        PullRequest pr1 = pullRequestService.addPullRequest("First add");
        PullRequest pr2 = pullRequestService.addPullRequest("Second");
        mockResultEndpoint.expectedMessageCount(2);
        mockResultEndpoint.expectedBodiesReceivedInAnyOrder(pr1, pr2);
        Thread.sleep(1 * 1000);

        mockResultEndpoint.assertIsSatisfied();
    }

    public class MockPullRequestProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            Message in = exchange.getIn();
            PullRequest pullRequest = (PullRequest) in.getBody();
            User pullRequestUser = pullRequest.getUser();

            pullRequest.getTitle();
            pullRequest.getHtmlUrl();
            pullRequest.getUser().getLogin();
            pullRequest.getUser().getHtmlUrl();
            LOG.debug("Got PullRequest {} [{}] From {}", pullRequest.getHtmlUrl(), pullRequest.getTitle(),
                    pullRequestUser.getLogin());
        }
    }
}
