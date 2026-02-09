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
package org.apache.camel.component.github2.producer;

import org.apache.camel.Exchange;
import org.apache.camel.component.github2.GitHub2Constants;
import org.apache.camel.component.github2.GitHub2Endpoint;
import org.apache.camel.support.ExchangeHelper;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer endpoint that adds a comment on a GitHub pull request.
 *
 * The endpoint requires the "GitHubPullRequest" header, identifying the pull request number (integer). If the
 * "GitHubInResponseTo" header is provided (identifying a comment ID), the comment will be a reply to that comment (if
 * supported).
 */
public class PullRequestCommentProducer extends AbstractGitHub2Producer {

    private static final Logger LOG = LoggerFactory.getLogger(PullRequestCommentProducer.class);

    public PullRequestCommentProducer(GitHub2Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Integer pullRequestNumber = exchange.getIn().getHeader(GitHub2Constants.GITHUB_PULLREQUEST, Integer.class);
        String text = exchange.getIn().getBody(String.class);

        GHPullRequest pullRequest = getRepository().getPullRequest(pullRequestNumber);

        // Create a comment on the pull request
        GHIssueComment response = pullRequest.comment(text);

        // support InOut
        ExchangeHelper.setOutBodyPatternAware(exchange, response);
    }
}
