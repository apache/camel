/**
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
package org.apache.camel.component.github.producer;

import org.apache.camel.Exchange;
import org.apache.camel.component.github.GitHubEndpoint;
import org.eclipse.egit.github.core.Comment;
import org.eclipse.egit.github.core.service.IssueService;
import org.eclipse.egit.github.core.service.PullRequestService;

/**
 * Producer endpoint that adds one of two types of comments on a GitHub pullrequest:
 * 
 * 1.) Response to an in-line comment made on a pull request commit review.  To use, include the
 *     "GitHubInResponseTo" header, identifying the comment ID (integer) that you're responding to.
 * 2.) General comment on the pull request issue itself.
 * 
 * Both endpoints require the "GitHubPullRequest" header, identifying the pull request number (integer).
 */
public class PullRequestCommentProducer extends AbstractGitHubProducer {
    private PullRequestService pullRequestService = null;

    private IssueService issueService = null;

    public PullRequestCommentProducer(GitHubEndpoint endpoint) throws Exception {
        super(endpoint);
        
        pullRequestService = new PullRequestService();
        initService(pullRequestService);
        issueService = new IssueService();
        initService(issueService);
    }

    public void process(Exchange exchange) throws Exception {
        Integer pullRequestNumber = exchange.getIn().getHeader("GitHubPullRequest", Integer.class);
        Integer inResponseTo = exchange.getIn().getHeader("GitHubInResponseTo", Integer.class);
        String text = exchange.getIn().getBody(String.class);
        
        Comment response;
        if (inResponseTo != null && inResponseTo > 0) {
        	response = pullRequestService.replyToComment(getRepository(), pullRequestNumber, inResponseTo, text);
        } else {
            // Otherwise, just comment on the pull request itself.
        	response = issueService.createComment(getRepository(), pullRequestNumber, text);
        }
        
        // support InOut
        if (exchange.getPattern().isOutCapable()) {
            // copy the header of in message to the out message
            exchange.getOut().copyFrom(exchange.getIn());
            exchange.getOut().setBody(response);
        }
    }

}
