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

package org.apache.camel.component.jira.consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.Priority;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import io.atlassian.util.concurrent.Promise;
import io.atlassian.util.concurrent.Promises;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jira.JiraComponent;
import org.apache.camel.component.jira.JiraConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.camel.component.jira.JiraConstants.JIRA;
import static org.apache.camel.component.jira.JiraConstants.JIRA_REST_CLIENT_FACTORY;
import static org.apache.camel.component.jira.JiraTestConstants.*;
import static org.apache.camel.component.jira.Utils.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WatchUpdatesConsumerTest extends CamelTestSupport {
    private final List<Issue> issues = new ArrayList<>();

    @Mock
    private JiraRestClient jiraClient;

    @Mock
    private JiraRestClientFactory jiraRestClientFactory;

    @Mock
    private SearchRestClient searchRestClient;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind(JIRA_REST_CLIENT_FACTORY, jiraRestClientFactory);
    }

    @BeforeEach
    public void beforeEach() {
        issues.clear();
        issues.add(createIssue(1L));
        issues.add(createIssue(2L));
        issues.add(createIssue(3L));
    }

    public void setMocks() {
        SearchResult result = new SearchResult(0, 50, 100, issues);
        Promise<SearchResult> promiseSearchResult = Promises.promise(result);

        when(jiraClient.getSearchClient()).thenReturn(searchRestClient);
        when(jiraRestClientFactory.createWithBasicHttpAuthentication(any(), any(), any())).thenReturn(jiraClient);
        when(searchRestClient.searchJql(any(), any(), any(), any())).thenReturn(promiseSearchResult);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        setMocks();
        CamelContext camelContext = super.createCamelContext();
        camelContext.disableJMX();
        JiraComponent component = new JiraComponent(camelContext);
        camelContext.addComponent(JIRA, component);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jira://watchUpdates?jiraUrl=" + JIRA_CREDENTIALS
                     + "&jql=project=" + PROJECT + "&delay=500&watchedFields=" + WATCHED_COMPONENTS)
                        .to(mockResult);
            }
        };
    }

    @Test
    public void emptyAtStartupTest() throws Exception {
        mockResult.expectedMessageCount(0);
        mockResult.assertIsSatisfied();
    }

    @Test
    public void singleChangeTest() throws Exception {
        Issue issue = setPriority(issues.get(0), new Priority(
                null, 4L, "High", null, null, null));
        reset(searchRestClient);
        AtomicInteger searchCount = new AtomicInteger();
        when(searchRestClient.searchJql(any(), any(), any(), any())).then(invocation -> {
            if (searchCount.incrementAndGet() == 2) {
                issues.remove(0);
                issues.add(0, issue);
            }
            SearchResult result = new SearchResult(0, 50, 100, issues);
            return Promises.promise(result);
        });

        mockResult.expectedBodiesReceived(issue.getPriority());
        mockResult.expectedHeaderReceived(JiraConstants.ISSUE_CHANGED, "Priority");
        mockResult.expectedHeaderReceived(JiraConstants.ISSUE_KEY, "TST-1");
        mockResult.expectedMessageCount(1);
        mockResult.assertIsSatisfied(0);
    }

    @Test
    public void multipleChangesWithAddedNewIssueTest() throws Exception {
        final Issue issue = transitionIssueDone(issues.get(1));
        final Issue issue2 = setPriority(issues.get(2), new Priority(
                null, 2L, "High", null, null, null));

        reset(searchRestClient);
        AtomicInteger searchCount = new AtomicInteger();
        when(searchRestClient.searchJql(any(), any(), any(), any())).then(invocation -> {
            if (searchCount.incrementAndGet() == 2) {
                issues.add(createIssue(4L));
                issues.remove(1);
                issues.add(1, issue);
                issues.remove(2);
                issues.add(2, issue2);
            }

            SearchResult result = new SearchResult(0, 50, 4, issues);
            return Promises.promise(result);
        });

        mockResult.expectedMessageCount(3);
        mockResult.expectedBodiesReceivedInAnyOrder(issue.getStatus(), issue.getResolution(), issue2.getPriority());
        mockResult.assertIsSatisfied(1000);
    }

    @Test
    public void multipleChangesWithAddedAndUpdatedNewIssueTest() throws Exception {
        final Issue issue = transitionIssueDone(issues.get(1));
        final Issue issue2 = setPriority(issues.get(2), new Priority(
                null, 2L, "High", null, null, null));
        final Issue newIssue = createIssue(4L);

        reset(searchRestClient);
        AtomicInteger searchCount = new AtomicInteger();
        when(searchRestClient.searchJql(any(), any(), any(), any())).then(invocation -> {
            if (searchCount.incrementAndGet() == 2) {
                issues.add(newIssue);
                issues.remove(1);
                issues.add(1, issue);
                issues.remove(2);
                issues.add(2, issue2);
            }

            SearchResult result = new SearchResult(0, 50, 3, issues);
            return Promises.promise(result);
        });

        mockResult.expectedMessageCount(3);
        mockResult.expectedBodiesReceivedInAnyOrder(issue.getStatus(), issue.getResolution(), issue2.getPriority());
        mockResult.assertIsSatisfied(1000);

        mockResult.reset();
        mockResult.expectedMessageCount(2);

        AtomicBoolean searched = new AtomicBoolean();
        Issue resolvedNewIssue = transitionIssueDone(newIssue);
        reset(searchRestClient);
        when(searchRestClient.searchJql(any(), any(), any(), any())).then(invocation -> {
            if (!searched.get()) {
                issues.remove(3);
                issues.add(3, resolvedNewIssue);
                searched.set(true);
            }

            SearchResult result = new SearchResult(0, 50, 4, issues);

            return Promises.promise(result);
        });

        mockResult.expectedBodiesReceivedInAnyOrder(resolvedNewIssue.getStatus(), resolvedNewIssue.getResolution());
        mockResult.assertIsSatisfied(1000);
    }
}
