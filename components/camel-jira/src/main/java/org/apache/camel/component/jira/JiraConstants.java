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
package org.apache.camel.component.jira;

import org.apache.camel.spi.Metadata;

public interface JiraConstants {

    String JIRA = "jira";
    String ACCESS_TOKEN = "accessToken";
    String VERIFICATION_CODE = "verificationCode";
    String JIRA_URL = "jiraUrl";
    String PRIVATE_KEY = "privateKey";
    String CONSUMER_KEY = "consumerKey";
    @Metadata(label = "producer", description = "The assignee's id of the issue", javaType = "String")
    String ISSUE_ASSIGNEE_ID = "IssueAssigneeId";
    @Metadata(label = "producer", description = "The assignee's name of the issue", javaType = "String")
    String ISSUE_ASSIGNEE = "IssueAssignee";
    @Metadata(label = "producer", description = "The comma separated list of the issue's components", javaType = "String")
    String ISSUE_COMPONENTS = "IssueComponents";
    String ISSUE_COMMENT = "IssueComment";
    @Metadata(label = "consumer", description = "The name of the updated field (i.e Status)", javaType = "String")
    String ISSUE_CHANGED = "IssueChanged";
    @Metadata(description = "The id of the issue", javaType = "String")
    String ISSUE_KEY = "IssueKey";
    @Metadata(label = "producer", description = "The priority's id of the issue", javaType = "Long")
    String ISSUE_PRIORITY_ID = "IssuePriorityId";
    @Metadata(label = "producer", description = "The priority's name of the issue", javaType = "String")
    String ISSUE_PRIORITY_NAME = "IssuePriorityName";
    @Metadata(label = "producer", description = "The project's id of the issue", javaType = "String")
    String ISSUE_PROJECT_KEY = "ProjectKey";
    @Metadata(label = "producer", description = "The summary of the issue", javaType = "String")
    String ISSUE_SUMMARY = "IssueSummary";
    @Metadata(label = "producer", description = "The transition id", javaType = "Integer")
    String ISSUE_TRANSITION_ID = "IssueTransitionId";
    @Metadata(label = "producer", description = "The type's id of the issue", javaType = "Long")
    String ISSUE_TYPE_ID = "IssueTypeId";
    @Metadata(label = "producer", description = "The type's name of the issue", javaType = "String")
    String ISSUE_TYPE_NAME = "IssueTypeName";
    @Metadata(label = "consumer", description = "The list of all issue keys that are watched in the time of update",
              javaType = "String")
    String ISSUE_WATCHED_ISSUES = "IssueWatchedIssues";
    @Metadata(label = "producer", description = "The comma separated list of watchers to add to the issue", javaType = "String")
    String ISSUE_WATCHERS_ADD = "IssueWatchersAdd";
    @Metadata(label = "producer", description = "The watchers of the issue to remove", javaType = "String")
    String ISSUE_WATCHERS_REMOVE = "IssueWatchersRemove";
    String JIRA_REST_CLIENT_FACTORY = "JiraRestClientFactory";
    @Metadata(label = "producer", description = "The id of the parent issue", javaType = "String")
    String PARENT_ISSUE_KEY = "ParentIssueKey";
    @Metadata(label = "producer", description = "The id of the child issue", javaType = "String")
    String CHILD_ISSUE_KEY = "ChildIssueKey";
    @Metadata(label = "producer", description = "The type of link", javaType = "String")
    String LINK_TYPE = "linkType";
    @Metadata(label = "producer", description = "The minutes spent", javaType = "int", defaultValue = "-1")
    String MINUTES_SPENT = "minutesSpent";
}
