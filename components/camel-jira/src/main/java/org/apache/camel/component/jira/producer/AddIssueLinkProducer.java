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

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.input.LinkIssuesInput;
import org.apache.camel.Exchange;
import org.apache.camel.component.jira.JiraEndpoint;
import org.apache.camel.support.DefaultProducer;

import static org.apache.camel.component.jira.JiraConstants.CHILD_ISSUE_KEY;
import static org.apache.camel.component.jira.JiraConstants.LINK_TYPE;
import static org.apache.camel.component.jira.JiraConstants.PARENT_ISSUE_KEY;

public class AddIssueLinkProducer extends DefaultProducer {

    public AddIssueLinkProducer(JiraEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) {
        String parentIssueKey = exchange.getIn().getHeader(PARENT_ISSUE_KEY, String.class);
        if (parentIssueKey == null) {
            throw new IllegalArgumentException("Missing exchange input header named " + PARENT_ISSUE_KEY);
        }
        String childIssueKey = exchange.getIn().getHeader(CHILD_ISSUE_KEY, String.class);
        if (childIssueKey == null) {
            throw new IllegalArgumentException("Missing exchange input header named " + CHILD_ISSUE_KEY);
        }
        String linkType = exchange.getIn().getHeader(LINK_TYPE, String.class);
        if (linkType == null) {
            throw new IllegalArgumentException("Missing exchange input header named " + LINK_TYPE);
        }
        String parentIssueComment = exchange.getIn().getBody(String.class);

        JiraRestClient client = ((JiraEndpoint) getEndpoint()).getClient();
        IssueRestClient issueClient = client.getIssueClient();

        Comment comment = null;

        if (parentIssueComment != null) {
            comment = Comment.valueOf(parentIssueComment);
        }

        LinkIssuesInput linkIssuesInput = new LinkIssuesInput(parentIssueKey, childIssueKey, linkType, comment);
        issueClient.linkIssue(linkIssuesInput);
    }
}
