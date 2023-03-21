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

import java.util.ArrayList;
import java.util.List;

import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.jira.JiraEndpoint;
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
        List<Comment> newComments = getComments();
        int max = newComments.size() - 1;
        // retrieve from last to first item LIFO
        for (int i = max; i > -1; i--) {
            Comment newComment = newComments.get(i);
            Exchange e = createExchange(true);
            e.getIn().setBody(newComment);
            getProcessor().process(e);
        }
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
    private List<Comment> getComments() {
        LOG.debug("Start: Jira NewCommentsConsumer: retrieving issue comments. Last comment id: {}", lastCommentId);
        List<Comment> newComments = new ArrayList<>();
        List<Issue> issues = getIssues();
        for (Issue issue : issues) {
            Issue fullIssue = client().getIssueClient().getIssue(issue.getKey()).claim();
            for (Comment comment : fullIssue.getComments()) {
                if (comment.getId() > lastCommentId) {
                    newComments.add(comment);
                }
            }
        }
        for (Comment c : newComments) {
            if (c.getId() > lastCommentId) {
                lastCommentId = c.getId();
            }
        }
        LOG.debug("End: Jira NewCommentsConsumer: retrieving issue comments. {} new comments since last run.",
                newComments.size());
        return newComments;
    }
}
