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
package org.apache.camel.component.github2.consumer;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.github2.GitHub2Constants;
import org.apache.camel.component.github2.GitHub2Endpoint;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PullRequestConsumer extends AbstractGitHub2Consumer {

    private static final Logger LOG = LoggerFactory.getLogger(PullRequestConsumer.class);

    private int lastOpenPullRequest;

    public PullRequestConsumer(GitHub2Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.info("GitHub PullRequestConsumer: Indexing current pull requests...");
        List<GHPullRequest> pullRequests = getRepository().getPullRequests(GHIssueState.OPEN);
        if (!pullRequests.isEmpty()) {
            lastOpenPullRequest = pullRequests.get(0).getNumber();
        }
    }

    @Override
    protected int poll() throws Exception {
        List<GHPullRequest> openPullRequests = getRepository().getPullRequests(GHIssueState.OPEN);

        // In the end, we want PRs oldest to newest
        ArrayDeque<GHPullRequest> newPullRequests = new ArrayDeque<>();
        for (GHPullRequest pullRequest : openPullRequests) {
            if (pullRequest.getNumber() > lastOpenPullRequest) {
                newPullRequests.push(pullRequest);
            } else {
                break;
            }
        }

        if (!newPullRequests.isEmpty()) {
            lastOpenPullRequest = openPullRequests.get(0).getNumber();
        }

        Queue<Object> exchanges = new ArrayDeque<>();
        while (!newPullRequests.isEmpty()) {
            GHPullRequest newPullRequest = newPullRequests.pop();
            Exchange e = createExchange(true);

            e.getIn().setBody(newPullRequest);

            // Required by the producers. Set it here for convenience.
            e.getIn().setHeader(GitHub2Constants.GITHUB_PULLREQUEST, newPullRequest.getNumber());
            if (newPullRequest.getHead() != null) {
                e.getIn().setHeader(GitHub2Constants.GITHUB_PULLREQUEST_HEAD_COMMIT_SHA, newPullRequest.getHead().getSha());
            }
            exchanges.add(e);
        }
        return processBatch(exchanges);
    }
}
