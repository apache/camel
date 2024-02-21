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

import java.util.ArrayList;
import java.util.List;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueFieldId;
import com.atlassian.jira.rest.client.api.domain.IssueType;
import com.atlassian.jira.rest.client.api.domain.Priority;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.component.jira.JiraEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.jira.JiraConstants.*;

public class AddIssueProducer extends DefaultProducer {

    public AddIssueProducer(JiraEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) {
        JiraRestClient client = ((JiraEndpoint) getEndpoint()).getClient();
        // required fields
        String projectKey = exchange.getIn().getHeader(ISSUE_PROJECT_KEY, String.class);
        Long issueTypeId = exchange.getIn().getHeader(ISSUE_TYPE_ID, Long.class);
        String issueTypeName = exchange.getIn().getHeader(ISSUE_TYPE_NAME, String.class);
        String summary = exchange.getIn().getHeader(ISSUE_SUMMARY, String.class);
        // optional fields
        String assigneeName = exchange.getIn().getHeader(ISSUE_ASSIGNEE, String.class);
        String assigneeId = exchange.getIn().getHeader(ISSUE_ASSIGNEE_ID, String.class);
        String priorityName = exchange.getIn().getHeader(ISSUE_PRIORITY_NAME, String.class);
        Long priorityId = exchange.getIn().getHeader(ISSUE_PRIORITY_ID, Long.class);
        String components = exchange.getIn().getHeader(ISSUE_COMPONENTS, String.class);
        String watchers = exchange.getIn().getHeader(ISSUE_WATCHERS_ADD, String.class);
        // search for issueTypeId from an issueTypeName
        if (issueTypeId == null && issueTypeName != null) {
            Iterable<IssueType> issueTypes = client.getMetadataClient().getIssueTypes().claim();
            for (IssueType type : issueTypes) {
                if (issueTypeName.equals(type.getName())) {
                    issueTypeId = type.getId();
                    break;
                }
            }
        }
        // search for priorityId from an priorityName
        if (priorityId == null && priorityName != null) {
            Iterable<Priority> priorities = client.getMetadataClient().getPriorities().claim();
            for (Priority pri : priorities) {
                if (priorityName.equals(pri.getName())) {
                    priorityId = pri.getId();
                    break;
                }
            }
        }
        if (projectKey == null) {
            throw new IllegalArgumentException("A valid project key is required.");
        }
        if (issueTypeId == null) {
            throw new IllegalArgumentException(
                    "A valid issue type id is required, actual: id(" + null + "), name(" + issueTypeName + ")");
        }

        if (summary == null) {
            throw new IllegalArgumentException("A summary field is required.");
        }

        IssueInputBuilder builder = new IssueInputBuilder(projectKey, issueTypeId);
        builder.setDescription(exchange.getIn().getBody(String.class));
        builder.setSummary(summary);
        if (ObjectHelper.isNotEmpty(components)) {
            String[] compArr = components.split(",");
            List<String> comps = new ArrayList<>(compArr.length);
            for (String s : compArr) {
                String c = s.trim();
                if (c.length() > 0) {
                    comps.add(c);
                }
            }
            builder.setComponentsNames(comps);
        }
        if (priorityId != null) {
            builder.setPriorityId(priorityId);
        }
        if (assigneeName != null) {
            builder.setAssigneeName(assigneeName);
        } else if (assigneeId != null) {
            builder.setFieldInput(
                    new FieldInput(IssueFieldId.ASSIGNEE_FIELD, ComplexIssueInputFieldValue.with("id", assigneeId)));
        }

        IssueRestClient issueClient = client.getIssueClient();
        BasicIssue issueCreated = issueClient.createIssue(builder.build()).claim();
        Issue issue = issueClient.getIssue(issueCreated.getKey()).claim();
        if (ObjectHelper.isNotEmpty(watchers)) {
            String[] watArr = watchers.split(",");
            for (String s : watArr) {
                String watcher = s.trim();
                if (watcher.length() > 0) {
                    issueClient.addWatcher(issue.getWatchers().getSelf(), watcher);
                }
            }
        }

        // support InOut
        ExchangeHelper.setInOutBodyPatternAware(exchange, issue);
    }

}
