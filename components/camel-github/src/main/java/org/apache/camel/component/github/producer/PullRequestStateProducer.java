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
import org.apache.camel.component.github.GitHubConstants;
import org.apache.camel.component.github.GitHubEndpoint;
import org.apache.camel.spi.Registry;
import org.eclipse.egit.github.core.CommitStatus;
import org.eclipse.egit.github.core.service.CommitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer endpoint that sets the commit status.
 *
 * The endpoint requires the "GitHubPullRequest" header, identifying the pull request number (integer),
 * and the "GitHubPullRequestHeadCommitSHA" header, identifying the commit SHA on which the state will be recorded.
 */
public class PullRequestStateProducer extends AbstractGitHubProducer {
    private static final transient Logger LOG = LoggerFactory.getLogger(PullRequestStateProducer.class);

    private CommitService commitService;

    private String state;
    private String targetUrl;

    public PullRequestStateProducer(GitHubEndpoint endpoint) throws Exception {
        super(endpoint);

        Registry registry = endpoint.getCamelContext().getRegistry();
        Object service = registry.lookupByName(GitHubConstants.GITHUB_COMMIT_SERVICE);
        if (service != null) {
            LOG.debug("Using CommitService found in registry " + service.getClass().getCanonicalName());
            commitService = (CommitService) service;
        } else {
            commitService = new CommitService();
        }
        initService(commitService);

        state = endpoint.getState();
        targetUrl = endpoint.getTargetUrl();
    }

    public void process(Exchange exchange) throws Exception {
        String pullRequestNumberSHA = exchange.getIn().getHeader(GitHubConstants.GITHUB_PULLREQUEST_HEAD_COMMIT_SHA, String.class);
        String text = exchange.getIn().getBody(String.class);

        CommitStatus status = new CommitStatus();

        if (state != null) {
            status.setState(state);
        }

        if (targetUrl != null) {
            status.setTargetUrl(targetUrl);
        }

        if (text != null) {
            status.setDescription(text);
        }

        CommitStatus response = commitService.createStatus(getRepository(), pullRequestNumberSHA, status);

        // copy the header of in message to the out message
        exchange.getOut().copyFrom(exchange.getIn());
        exchange.getOut().setBody(response);
    }

}
