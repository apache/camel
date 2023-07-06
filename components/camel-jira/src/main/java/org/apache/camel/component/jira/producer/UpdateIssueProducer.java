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
import com.atlassian.jira.rest.client.api.domain.IssueFieldId;
import com.atlassian.jira.rest.client.api.domain.IssueType;
import com.atlassian.jira.rest.client.api.domain.Priority;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.component.jira.JiraEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.jira.JiraConstants.ISSUE_ASSIGNEE;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_ASSIGNEE_ID;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_COMPONENTS;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_KEY;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_PRIORITY_ID;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_PRIORITY_NAME;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_SUMMARY;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_TYPE_ID;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_TYPE_NAME;

public class UpdateIssueProducer extends DefaultProducer {

    public UpdateIssueProducer(JiraEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) {
        JiraRestClient client = ((JiraEndpoint) getEndpoint()).getClient();
        String issueKey = exchange.getIn().getHeader(ISSUE_KEY, String.class);
        if (issueKey == null) {
            throw new IllegalArgumentException(
                    "Missing exchange input header named \'IssueKey\', it should specify the issue key.");
        }
        Long issueTypeId = exchange.getIn().getHeader(ISSUE_TYPE_ID, Long.class);
        String issueTypeName = exchange.getIn().getHeader(ISSUE_TYPE_NAME, String.class);
        String summary = exchange.getIn().getHeader(ISSUE_SUMMARY, String.class);
        String assigneeName = exchange.getIn().getHeader(ISSUE_ASSIGNEE, String.class);
        String assigneeId = exchange.getIn().getHeader(ISSUE_ASSIGNEE_ID, String.class);
        String priorityName = exchange.getIn().getHeader(ISSUE_PRIORITY_NAME, String.class);
        Long priorityId = exchange.getIn().getHeader(ISSUE_PRIORITY_ID, Long.class);
        String components = exchange.getIn().getHeader(ISSUE_COMPONENTS, String.class);
        if (issueTypeId == null && issueTypeName != null) {
            Iterable<IssueType> issueTypes = client.getMetadataClient().getIssueTypes().claim();
            for (IssueType type : issueTypes) {
                if (issueTypeName.equals(type.getName())) {
                    issueTypeId = type.getId();
                    break;
                }
            }
        }
        if (priorityId == null && priorityName != null) {
            Iterable<Priority> priorities = client.getMetadataClient().getPriorities().claim();
            for (Priority pri : priorities) {
                if (priorityName.equals(pri.getName())) {
                    priorityId = pri.getId();
                    break;
                }
            }
        }
        IssueInputBuilder builder = new IssueInputBuilder();
        if (issueTypeId != null) {
            builder.setIssueTypeId(issueTypeId);
        }
        if (summary != null) {
            builder.setSummary(summary);
        }
        String description = exchange.getIn().getBody(String.class);
        if (description != null) {
            builder.setDescription(description);
        }
        if (ObjectHelper.isNotEmpty(components)) {
            String[] compArr = components.split(",");
            List<String> comps = new ArrayList<>(compArr.length);
            for (String s : compArr) {
                comps.add(s.trim());
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
        issueClient.updateIssue(issueKey, builder.build()).claim();
    }

}
