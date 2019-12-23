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
package org.apache.camel.component.github.producer;

import org.apache.camel.Exchange;
import org.apache.camel.component.github.GitHubConstants;
import org.apache.camel.component.github.GitHubEndpoint;
import org.apache.camel.spi.Registry;
import org.eclipse.egit.github.core.Comment;
import org.eclipse.egit.github.core.service.IssueService;
import org.eclipse.egit.github.core.service.PullRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer endpoint that adds one of two types of comments on a GitHub pull request:
 * 
 * 1.) Response to an in-line comment made on a pull request commit review.  To use, include the
 *     "GitHubInResponseTo" header, identifying the comment ID (integer) that you're responding to.
 * 2.) General comment on the pull request issue itself.
 * 
 * Both endpoints require the "GitHubPullRequest" header, identifying the pull request number (integer).
 */
public class PullRequestCommentProducer extends AbstractGitHubProducer {
    private static final transient Logger LOG = LoggerFactory.getLogger(PullRequestCommentProducer.class);
    private PullRequestService pullRequestService;

    private IssueService issueService;

    public PullRequestCommentProducer(GitHubEndpoint endpoint) throws Exception {
        super(endpoint);
        
        Registry registry = endpoint.getCamelContext().getRegistry();
        Object service = registry.lookupByName(GitHubConstants.GITHUB_PULL_REQUEST_SERVICE);
        if (service != null) {
            LOG.debug("Using PullRequestService found in registry {}", service.getClass().getCanonicalName());
            pullRequestService = (PullRequestService) service;
        } else {
            pullRequestService = new PullRequestService();
        }
        initService(pullRequestService);

        service = registry.lookupByName("githbIssueService");
        if (service != null) {
            issueService = (IssueService) service;
        } else {
            issueService = new IssueService();
        }
        initService(issueService);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Integer pullRequestNumber = exchange.getIn().getHeader(GitHubConstants.GITHUB_PULLREQUEST, Integer.class);
        Integer inResponseTo = exchange.getIn().getHeader(GitHubConstants.GITHUB_INRESPONSETO, Integer.class);
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
