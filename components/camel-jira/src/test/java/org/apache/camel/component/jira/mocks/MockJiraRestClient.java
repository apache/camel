/**
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
package org.apache.camel.component.jira.mocks;

import com.atlassian.jira.rest.client.ComponentRestClient;
import com.atlassian.jira.rest.client.IssueRestClient;
import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.MetadataRestClient;
import com.atlassian.jira.rest.client.ProjectRestClient;
import com.atlassian.jira.rest.client.ProjectRolesRestClient;
import com.atlassian.jira.rest.client.SearchRestClient;
import com.atlassian.jira.rest.client.SessionRestClient;
import com.atlassian.jira.rest.client.UserRestClient;
import com.atlassian.jira.rest.client.VersionRestClient;
import com.sun.jersey.client.apache.ApacheHttpClient;

public class MockJiraRestClient implements JiraRestClient {
    MockSearchRestClient mockSearchRestClient = new MockSearchRestClient();
    MockIssueClient mockIssueClient = new MockIssueClient(mockSearchRestClient);

    @Override
    public IssueRestClient getIssueClient() {
        return mockIssueClient;
    }

    @Override
    public SessionRestClient getSessionClient() {
        return null;
    }

    @Override
    public UserRestClient getUserClient() {
        return null;
    }

    @Override
    public ProjectRestClient getProjectClient() {
        return null;
    }

    @Override
    public ComponentRestClient getComponentClient() {
        return null;
    }

    @Override
    public MetadataRestClient getMetadataClient() {
        return null;
    }

    @Override
    public SearchRestClient getSearchClient() {
        return mockSearchRestClient;
    }

    @Override
    public VersionRestClient getVersionRestClient() {
        return null;
    }

    @Override
    public ProjectRolesRestClient getProjectRolesRestClient() {
        return null;
    }

    @Override
    public ApacheHttpClient getTransportClient() {
        return null;
    }
}
