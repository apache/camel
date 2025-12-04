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

package org.apache.camel.component.jira.consumer;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.domain.Comment;
import org.apache.camel.Processor;
import org.apache.camel.component.jira.JiraEndpoint;
import org.apache.camel.util.CastUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes new comments on JIRA issues.
 *
 * NOTE: In your JQL, try to optimize the query as much as possible! For example, the JIRA Toolkit Plugin includes a //
 * "Number of comments" custom field -- use '"Number of comments" > 0' in your query. Also try to minimize based on //
 * state (status=Open), increase the polling delay, etc. We have to do a separate query for *every single* resulting
 * ticket in order to load its comments! For large organizations, the JIRA API can be significantly slow.
 */
public class NewCommentsConsumer extends AbstractJiraConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(NewCommentsConsumer.class);

    private Long lastCommentId = -1L;

    public NewCommentsConsumer(JiraEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int doPoll() throws Exception {
        Queue<Comment> newComments = getComments();
        processBatch(CastUtils.cast(newComments));
        return newComments.size();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // read the actual comments, the next poll outputs only the new comments added after the route start
        getComments();
    }

    // In the end, we want *new* comments oldest to newest.
    @SuppressWarnings("ConstantConditions")
    private Queue<Comment> getComments() {
        LOG.debug("Start: Jira NewCommentsConsumer: retrieving issue comments. Last comment id: {}", lastCommentId);
        IssueRestClient client = getEndpoint().getClient().getIssueClient();

        LinkedList<Comment> newComments = getIssues().stream()
                .map(issue -> client.getIssue(issue.getKey()).claim())
                .flatMap(issue -> StreamSupport.stream(issue.getComments().spliterator(), false))
                .filter(comment -> comment.getId() > lastCommentId)
                .collect(Collectors.toCollection(LinkedList::new));

        Collections.reverse(newComments);

        lastCommentId = newComments.stream().mapToLong(Comment::getId).max().orElse(lastCommentId);
        LOG.debug(
                "End: Jira NewCommentsConsumer: retrieving issue comments. {} new comments since last run.",
                newComments.size());
        return newComments;
    }
}
