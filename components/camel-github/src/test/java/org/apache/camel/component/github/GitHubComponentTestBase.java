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
package org.apache.camel.component.github;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.component.github.services.MockCommitService;
import org.apache.camel.component.github.services.MockEventService;
import org.apache.camel.component.github.services.MockIssueService;
import org.apache.camel.component.github.services.MockPullRequestService;
import org.apache.camel.component.github.services.MockRepositoryService;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GitHubComponentTestBase extends CamelTestSupport {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @BindToRegistry(GitHubConstants.GITHUB_COMMIT_SERVICE)
    protected MockCommitService commitService = new MockCommitService();
    @BindToRegistry(GitHubConstants.GITHUB_REPOSITORY_SERVICE)
    protected MockRepositoryService repositoryService = new MockRepositoryService();
    @BindToRegistry(GitHubConstants.GITHUB_PULL_REQUEST_SERVICE)
    protected MockPullRequestService pullRequestService = new MockPullRequestService();
    @BindToRegistry(GitHubConstants.GITHUB_ISSUE_SERVICE)
    protected MockIssueService issueService = new MockIssueService(pullRequestService);
    @BindToRegistry(GitHubConstants.GITHUB_EVENT_SERVICE)
    protected MockEventService eventService = new MockEventService();

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        GitHubComponent ghc = context.getComponent("github", GitHubComponent.class);
        ghc.setOauthToken("mytoken");
        return context;
    }

    @Test
    public void emptyAtStartupTest() throws Exception {
        mockResultEndpoint.expectedMessageCount(0);
        mockResultEndpoint.assertIsSatisfied();
    }
}
