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
package org.apache.camel.component.github2;

import org.apache.camel.spi.Metadata;

public interface GitHub2Constants {

    String GITHUB_CLIENT = "github2Client";

    @Metadata(description = "The pull request", javaType = "org.kohsuke.github.GHPullRequest or Integer")
    String GITHUB_PULLREQUEST = "GitHubPullRequest";
    @Metadata(label = "producer", description = "The id of the comment to reply to", javaType = "Long")
    String GITHUB_INRESPONSETO = "GitHubInResponseTo";
    @Metadata(description = "The sha of the head of the pull request", javaType = "String")
    String GITHUB_PULLREQUEST_HEAD_COMMIT_SHA = "GitHubPullRequestHeadCommitSHA";
    @Metadata(label = "producer", description = "The title of the issue", javaType = "String")
    String GITHUB_ISSUE_TITLE = "GitHubIssueTitle";

    @Metadata(label = "consumer", description = "The commit author", javaType = "String")
    String GITHUB_COMMIT_AUTHOR = "CamelGitHubCommitAuthor";
    @Metadata(label = "consumer", description = "The committer name", javaType = "String")
    String GITHUB_COMMIT_COMMITTER = "CamelGitHubCommitCommitter";
    @Metadata(label = "consumer", description = "The commit sha", javaType = "String")
    String GITHUB_COMMIT_SHA = "CamelGitHubCommitSha";
    @Metadata(label = "consumer", description = "The commit URL", javaType = "String")
    String GITHUB_COMMIT_URL = "CamelGitHubCommitUrl";
    @Metadata(label = "consumer", description = "The event payload",
              javaType = "org.kohsuke.github.GHEventPayload")
    String GITHUB_EVENT_PAYLOAD = "CamelGitHubEventPayload";
}
