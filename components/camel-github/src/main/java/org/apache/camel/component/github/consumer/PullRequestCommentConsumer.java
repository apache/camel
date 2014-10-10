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

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.github.GitHubEndpoint;
import org.apache.camel.spi.Registry;
import org.eclipse.egit.github.core.Comment;
import org.eclipse.egit.github.core.CommitComment;
import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.service.IssueService;
import org.eclipse.egit.github.core.service.PullRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PullRequestCommentConsumer extends AbstractGitHubConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(PullRequestCommentConsumer.class);

    private PullRequestService pullRequestService;

    private IssueService issueService;
    
    private List<Long> commentIds = new ArrayList<Long>();
    
    public PullRequestCommentConsumer(GitHubEndpoint endpoint, Processor processor) throws Exception {
        super(endpoint, processor);

        Registry registry = endpoint.getCamelContext().getRegistry();
        Object service = registry.lookupByName("githubPullRequestService");
        if (service !=null) {
            LOG.debug("Using PullRequestService found in registry " + service.getClass().getCanonicalName());
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

        LOG.info("GitHub PullRequestCommentConsumer: Indexing current pull request comments...");
        List<PullRequest> pullRequests = pullRequestService.getPullRequests(getRepository(), "open");
        for (PullRequest pullRequest : pullRequests) {
            List<CommitComment> commitComments = pullRequestService.getComments(getRepository(),
                                                                                pullRequest.getNumber());
            for (Comment comment : commitComments) {
                commentIds.add(comment.getId());
            }
            List<Comment> comments = issueService.getComments(getRepository(), pullRequest.getNumber());
            for (Comment comment : comments) {
                commentIds.add(comment.getId());
            }
        }
    }

    @Override
    protected int poll() throws Exception {
        List<PullRequest> pullRequests = pullRequestService.getPullRequests(getRepository(), "open");
        // In the end, we want comments oldest to newest.
        Stack<Comment> newComments = new Stack<Comment>();
        for (PullRequest pullRequest : pullRequests) {
            List<CommitComment> commitComments = pullRequestService.getComments(getRepository(), pullRequest.getNumber());
            for (Comment comment : commitComments) {
                if (!commentIds.contains(comment.getId())) {
                    newComments.add(comment);
                    commentIds.add(comment.getId());
                }
            }
            List<Comment> comments = issueService.getComments(getRepository(), pullRequest.getNumber());
            for (Comment comment : comments) {
                if (!commentIds.contains(comment.getId())) {
                    newComments.add(comment);
                    commentIds.add(comment.getId());
                }
            }
        }
        
        while (!newComments.empty()) {
            Comment newComment = newComments.pop();
            Exchange e = getEndpoint().createExchange();
            e.getIn().setBody(newComment);
            getProcessor().process(e);
        }
        return newComments.size();
    }
}
