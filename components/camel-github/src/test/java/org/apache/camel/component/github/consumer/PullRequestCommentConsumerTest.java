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
import org.apache.camel.component.github.GitHubComponent;
import org.apache.camel.component.github.GitHubComponentTestBase;
import org.eclipse.egit.github.core.Comment;
import org.eclipse.egit.github.core.CommitComment;
import org.eclipse.egit.github.core.PullRequest;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PullRequestCommentConsumerTest extends GitHubComponentTestBase {
    protected static final Logger LOG = LoggerFactory.getLogger(PullRequestCommentConsumerTest.class);

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                context.addComponent("github", new GitHubComponent());
                from("github://pullRequestComment?username=someguy&password=apassword&repoOwner=anotherguy&repoName=somerepo")
                        .process(new PullRequestCommentProcessor())
                        .to(mockResultEndpoint);
            }
        };
    }

    @Test
    public void pullRequestCommentTest() throws Exception {
        PullRequest pr1 = pullRequestService.addPullRequest("First add");
        PullRequest pr2 = pullRequestService.addPullRequest("Second");
        CommitComment commitComment1 = pullRequestService.addComment(pr1.getId(), "First comment");
        CommitComment commitComment2 = pullRequestService.addComment(pr2.getId(), "Second comment");
        mockResultEndpoint.expectedBodiesReceivedInAnyOrder(commitComment1, commitComment2);

        Thread.sleep(1 * 1000);         // TODO do I need this?

        mockResultEndpoint.assertIsSatisfied();
    }


    public class PullRequestCommentProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Message in = exchange.getIn();
            Comment comment = (Comment) in.getBody();
            LOG.debug("Got Comment " + comment.getId() + " [" + comment.getBody() + "] from User [" + comment.getUser().getLogin() + "]");
        }
    }
}
