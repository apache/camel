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
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.github2.GitHub2Constants;
import org.apache.camel.component.github2.GitHub2Endpoint;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PullRequestCommentConsumer extends AbstractGitHub2Consumer {

    private static final Logger LOG = LoggerFactory.getLogger(PullRequestCommentConsumer.class);

    private long lastCommentId;

    public PullRequestCommentConsumer(GitHub2Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.info("GitHub PullRequestCommentConsumer: Indexing current comments...");
        List<GHPullRequest> pullRequests = getRepository().getPullRequests(GHIssueState.OPEN);
        for (GHPullRequest pr : pullRequests) {
            List<GHIssueComment> comments = pr.getComments();
            for (GHIssueComment comment : comments) {
                if (comment.getId() > lastCommentId) {
                    lastCommentId = comment.getId();
                }
            }
        }
    }

    @Override
    protected int poll() throws Exception {
        List<GHPullRequest> openPullRequests = getRepository().getPullRequests(GHIssueState.OPEN);

        List<GHIssueComment> newComments = new ArrayList<>();
        for (GHPullRequest pr : openPullRequests) {
            List<GHIssueComment> comments = pr.getComments();
            for (GHIssueComment comment : comments) {
                if (comment.getId() > lastCommentId) {
                    newComments.add(comment);
                }
            }
        }

        // Sort by ID to process oldest first
        newComments.sort((c1, c2) -> Long.compare(c1.getId(), c2.getId()));

        Queue<Object> exchanges = new ArrayDeque<>();
        for (GHIssueComment comment : newComments) {
            Exchange e = createExchange(true);
            e.getIn().setBody(comment);
            e.getIn().setHeader(GitHub2Constants.GITHUB_PULLREQUEST, comment.getParent().getNumber());
            exchanges.add(e);
            lastCommentId = comment.getId();
        }

        return processBatch(exchanges);
    }
}
