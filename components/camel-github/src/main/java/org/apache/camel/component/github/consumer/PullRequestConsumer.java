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
package org.apache.camel.component.github.consumer;

import java.util.List;
import java.util.Stack;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.github.GitHubConstants;
import org.apache.camel.component.github.GitHubEndpoint;
import org.apache.camel.spi.Registry;
import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.service.PullRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PullRequestConsumer extends AbstractGitHubConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(PullRequestConsumer.class);

    private PullRequestService pullRequestService;

    private int lastOpenPullRequest;

    public PullRequestConsumer(GitHubEndpoint endpoint, Processor processor) throws Exception {
        super(endpoint, processor);

        Registry registry = endpoint.getCamelContext().getRegistry();
        Object service = registry.lookupByName(GitHubConstants.GITHUB_PULL_REQUEST_SERVICE);
        if (service != null) {
            LOG.debug("Using PullRequestService found in registry " + service.getClass().getCanonicalName());
            pullRequestService = (PullRequestService) service;
        } else {
            pullRequestService = new PullRequestService();
        }

        initService(pullRequestService);

        LOG.info("GitHub PullRequestConsumer: Indexing current pull requests...");
        List<PullRequest> pullRequests = pullRequestService.getPullRequests(getRepository(), "open");
        if (pullRequests.size() > 0) {
            lastOpenPullRequest = pullRequests.get(0).getNumber();
        }
    }

    @Override
    protected int poll() throws Exception {
        List<PullRequest> openPullRequests = pullRequestService.getPullRequests(getRepository(), "open");
        // In the end, we want PRs oldest to newest.
        Stack<PullRequest> newPullRequests = new Stack<PullRequest>();
        for (PullRequest pullRequest : openPullRequests) {
            if (pullRequest.getNumber() > lastOpenPullRequest) {
                newPullRequests.push(pullRequest);
            } else {
                break;
            }
        }

        if (newPullRequests.size() > 0) {
            lastOpenPullRequest = openPullRequests.get(0).getNumber();
        }

        while (!newPullRequests.empty()) {
            PullRequest newPullRequest = newPullRequests.pop();
            Exchange e = getEndpoint().createExchange();

            e.getIn().setBody(newPullRequest);

            // Required by the producers.  Set it here for convenience.
            e.getIn().setHeader(GitHubConstants.GITHUB_PULLREQUEST, newPullRequest.getNumber());
            if (newPullRequest.getHead() != null) {
                e.getIn().setHeader(GitHubConstants.GITHUB_PULLREQUEST_HEAD_COMMIT_SHA, newPullRequest.getHead().getSha());
            }

            getProcessor().process(e);
        }
        return newPullRequests.size();
    }
}
