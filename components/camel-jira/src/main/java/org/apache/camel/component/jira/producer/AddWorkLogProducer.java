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
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.input.WorklogInput;
import org.apache.camel.Exchange;
import org.apache.camel.component.jira.JiraEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.joda.time.DateTime;

import static org.apache.camel.component.jira.JiraConstants.ISSUE_KEY;
import static org.apache.camel.component.jira.JiraConstants.MINUTES_SPENT;

public class AddWorkLogProducer extends DefaultProducer {

    private static final int DEFAULT_MINUTES_SPENT = -1;

    public AddWorkLogProducer(JiraEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) {
        String issueKey = exchange.getIn().getHeader(ISSUE_KEY, String.class);
        if (issueKey == null) {
            throw new IllegalArgumentException("Missing exchange input header named " + ISSUE_KEY);
        }
        String comment = exchange.getIn().getBody(String.class);
        if (comment == null) {
            throw new IllegalArgumentException("Missing exchange body, it should specify the string comment.");
        }
        int minutesSpent = exchange.getIn().getHeader(MINUTES_SPENT, DEFAULT_MINUTES_SPENT, int.class);
        if (DEFAULT_MINUTES_SPENT == minutesSpent) {
            throw new IllegalArgumentException("Missing exchange input header named " + MINUTES_SPENT);
        }

        JiraRestClient client = ((JiraEndpoint) getEndpoint()).getClient();
        IssueRestClient issueClient = client.getIssueClient();
        Issue issue = issueClient.getIssue(issueKey).claim();

        WorklogInput worklogInput = WorklogInput.create(issue.getSelf(), comment, new DateTime(), minutesSpent);

        issueClient.addWorklog(issue.getWorklogUri(), worklogInput);

    }
}
