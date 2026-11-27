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
    @Metadata(label = "producer",
              description = "The ID of the user to assign the issue to, which is the account ID on Jira Cloud, for"
                            + " addIssue and updateIssue. Not used when the CamelJiraIssueAssignee header is set.",
              javaType = "String")
    String ISSUE_ASSIGNEE_ID = "CamelJiraIssueAssigneeId";
    @Metadata(label = "producer",
              description = "The username of the user to assign the issue to, for addIssue and updateIssue. Jira"
                            + " Cloud identifies users by account ID instead, so use the CamelJiraIssueAssigneeId"
                            + " header there.",
              javaType = "String")
    String ISSUE_ASSIGNEE = "CamelJiraIssueAssignee";
    @Metadata(label = "producer",
              description = "The comma-separated list of the names of the project components to set on the issue, for"
                            + " addIssue and updateIssue. For updateIssue, the list replaces the components of the"
                            + " issue.",
              javaType = "String")
    String ISSUE_COMPONENTS = "CamelJiraIssueComponents";
    String ISSUE_COMMENT = "CamelJiraIssueComment";
    @Metadata(label = "consumer",
              description = "The name of the watched field that changed, as written in the watchedFields option, for"
                            + " example Status. The watchUpdates consumer sets it.",
              javaType = "String")
    String ISSUE_CHANGED = "CamelJiraIssueChanged";
    @Metadata(description = "The key of the issue, for example CAMEL-1234. Required by every producer type except"
                            + " addIssue and addIssueLink. The watchUpdates consumer sets it to the key of the changed"
                            + " issue.",
              javaType = "String")
    String ISSUE_KEY = "CamelJiraIssueKey";
    @Metadata(label = "producer",
              description = "The ID of the priority of the issue, for addIssue and updateIssue. If not set, the"
                            + " producer looks up the ID of the priority that the CamelJiraIssuePriorityName header"
                            + " names.",
              javaType = "Long")
    String ISSUE_PRIORITY_ID = "CamelJiraIssuePriorityId";
    @Metadata(label = "producer",
              description = "The name of the priority of the issue, for example High, for addIssue and updateIssue."
                            + " Only used when the CamelJiraIssuePriorityId header is not set, and the name must match"
                            + " exactly.",
              javaType = "String")
    String ISSUE_PRIORITY_NAME = "CamelJiraIssuePriorityName";
    @Metadata(label = "producer",
              description = "The key of the project to create the issue in, for example CAMEL. Required for addIssue.",
              javaType = "String")
    String ISSUE_PROJECT_KEY = "CamelJiraIssueProjectKey";
    @Metadata(label = "producer",
              description = "The summary of the issue. Required for addIssue. For updateIssue, it replaces the"
                            + " summary of the issue.",
              javaType = "String")
    String ISSUE_SUMMARY = "CamelJiraIssueSummary";
    @Metadata(label = "producer",
              description = "The ID of the workflow transition to perform on the issue, for transitionIssue, which"
                            + " requires it. Jira lists the transitions of an issue at"
                            + " /rest/api/2/issue/{issueIdOrKey}/transitions.",
              javaType = "Integer")
    String ISSUE_TRANSITION_ID = "CamelJiraIssueTransitionId";
    @Metadata(label = "producer",
              description = "The ID of the issue type, for addIssue and updateIssue. If not set, the producer looks"
                            + " up the ID of the issue type that the CamelJiraIssueTypeName header names. addIssue"
                            + " requires one of the two headers.",
              javaType = "Long")
    String ISSUE_TYPE_ID = "CamelJiraIssueTypeId";
    @Metadata(label = "producer",
              description = "The name of the issue type, for example Bug, for addIssue and updateIssue. Only used"
                            + " when the CamelJiraIssueTypeId header is not set, and the name must match exactly.",
              javaType = "String")
    String ISSUE_TYPE_NAME = "CamelJiraIssueTypeName";
    @Metadata(label = "consumer",
              description = "The comma-separated list of the keys of the issues that the watchUpdates consumer"
                            + " watches. The consumer builds the list when it starts, and again when the number of"
                            + " issues that the JQL query returns changes.",
              javaType = "String")
    String ISSUE_WATCHED_ISSUES = "CamelJiraIssueWatchedIssues";
    @Metadata(label = "producer",
              description = "The comma-separated list of the usernames, or of the account IDs on Jira Cloud, of the"
                            + " users to add as watchers of the issue, for addIssue and watchers.",
              javaType = "String")
    String ISSUE_WATCHERS_ADD = "CamelJiraIssueWatchersAdd";
    @Metadata(label = "producer",
              description = "The comma-separated list of the usernames of the users to remove as watchers of the"
                            + " issue, for watchers.",
              javaType = "String")
    String ISSUE_WATCHERS_REMOVE = "CamelJiraIssueWatchersRemove";
    String JIRA_REST_CLIENT_FACTORY = "JiraRestClientFactory";
    @Metadata(label = "producer",
              description = "The key of the first issue of the link, which the producer sends as the inward issue."
                            + " Required for addIssueLink.",
              javaType = "String")
    String PARENT_ISSUE_KEY = "CamelJiraParentIssueKey";
    @Metadata(label = "producer",
              description = "The key of the second issue of the link, which the producer sends as the outward issue."
                            + " Required for addIssueLink.",
              javaType = "String")
    String CHILD_ISSUE_KEY = "CamelJiraChildIssueKey";
    @Metadata(label = "producer",
              description = "The name of the issue link type, for example Blocks or Duplicate. Required for"
                            + " addIssueLink.",
              javaType = "String")
    String LINK_TYPE = "CamelJiraLinkType";
    @Metadata(label = "producer",
              description = "The time spent on the issue in minutes, for the work log that addWorkLog adds, which"
                            + " requires it.",
              javaType = "int", defaultValue = "-1")
    String MINUTES_SPENT = "CamelJiraMinutesSpent";
}
