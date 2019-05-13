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
package org.apache.camel.component.jira.producer;

import java.net.URI;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import org.apache.camel.Exchange;
import org.apache.camel.component.jira.JiraEndpoint;
import org.apache.camel.support.DefaultProducer;

import static org.apache.camel.component.jira.JiraConstants.ISSUE_KEY;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_TRANSITION_ID;

public class TransitionIssueProducer extends DefaultProducer {

    public TransitionIssueProducer(JiraEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) {
        String issueKey = exchange.getIn().getHeader(ISSUE_KEY, String.class);
        String commentStr = exchange.getIn().getBody(String.class);
        // the list of transitions is available in /rest/api/2/issue/{issueIdOrKey}/transitions
        Integer transitionId = exchange.getIn().getHeader(ISSUE_TRANSITION_ID, Integer.class);
        if (issueKey == null) {
            throw new IllegalArgumentException("Missing exchange input header named \'IssueKey\', it should specify the issue key to add the comment to.");
        }
        if (transitionId == null) {
            throw new IllegalArgumentException("Missing exchange input header named \'IssueTransitionId\', it should specify the transition id.");
        }
        JiraRestClient client = ((JiraEndpoint) getEndpoint()).getClient();
        IssueRestClient issueClient = client.getIssueClient();
        Issue issue = issueClient.getIssue(issueKey).claim();
        TransitionInput transitionInput = new TransitionInput(transitionId);
        issueClient.transition(issue, transitionInput);
        // add a comment in an additional operation as using the TransitionInput doesn't add the comment.
        if (commentStr != null) {
            URI commentsUri = issue.getCommentsUri();
            // there is a bug in addComment, it doesn't use the author parameter to add the comment
            // it uses the authenticated username. https://ecosystem.atlassian.net/browse/JRJC-241
            issueClient.addComment(commentsUri, Comment.valueOf(commentStr));
        }
    }
}
