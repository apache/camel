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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.Priority;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import io.atlassian.util.concurrent.Promises;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jira.JiraComponent;
import org.apache.camel.component.jira.JiraConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.camel.component.jira.JiraConstants.JIRA;
import static org.apache.camel.component.jira.JiraConstants.JIRA_REST_CLIENT_FACTORY;
import static org.apache.camel.component.jira.JiraTestConstants.JIRA_CREDENTIALS;
import static org.apache.camel.component.jira.JiraTestConstants.PROJECT;
import static org.apache.camel.component.jira.JiraTestConstants.WATCHED_COMPONENTS;
import static org.apache.camel.component.jira.Utils.createIssue;
import static org.apache.camel.component.jira.Utils.setPriority;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * maxResults=0 (ignored, as for any value <= 0) on Jira Server / Data Center, with more issues than one page of the
 * search: the consumer reads every page instead of asking for pages of 0 issues at the same start index forever.
 */
@ExtendWith(MockitoExtension.class)
class WatchUpdatesConsumerMaxResultsZeroTest extends CamelTestSupport {

    private static final int ISSUES = 60;

    private final List<Issue> issues = new CopyOnWriteArrayList<>();

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

    @Override
    protected CamelContext createCamelContext() throws Exception {
        for (long id = 1; id <= ISSUES; id++) {
            issues.add(createIssue(id));
        }
        // the search of Jira Server / Data Center: a page of at most maxResults issues from startAt, and the total of
        // the search (maxResults=0 returns no issue, only the total)
        AtomicInteger sameStart = new AtomicInteger();
        AtomicInteger previousStart = new AtomicInteger(-1);
        when(searchRestClient.searchJql(any(), any(), any(), any())).then(invocation -> {
            int maxResults = invocation.getArgument(1);
            int startAt = invocation.getArgument(2);
            if (previousStart.getAndSet(startAt) == startAt && sameStart.incrementAndGet() > 10) {
                throw new IllegalStateException("The search is repeated at startAt " + startAt);
            }
            List<Issue> page = issues.subList(Math.min(startAt, ISSUES), Math.min(startAt + maxResults, ISSUES));
            return Promises.promise(new SearchResult(startAt, maxResults, ISSUES, List.copyOf(page)));
        });
        when(jiraClient.getSearchClient()).thenReturn(searchRestClient);
        when(jiraRestClientFactory.createWithBasicHttpAuthentication(any(), any(), any())).thenReturn(jiraClient);

        CamelContext camelContext = super.createCamelContext();
        camelContext.disableJMX();
        camelContext.addComponent(JIRA, new JiraComponent(camelContext));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jira://watchUpdates?jiraUrl=" + JIRA_CREDENTIALS + "&jql=project=" + PROJECT
                     + "&delay=500&maxResults=0&watchedFields=" + WATCHED_COMPONENTS)
                        .to(mockResult);
            }
        };
    }

    @Test
    void changeOfAnIssueOnTheSecondPageIsConsumed() throws Exception {
        Issue changed = setPriority(issues.get(54), new Priority(null, 4L, "High", null, null, null));
        mockResult.expectedBodiesReceived(changed.getPriority());
        mockResult.expectedHeaderReceived(JiraConstants.ISSUE_KEY, changed.getKey());

        issues.set(54, changed);

        mockResult.assertIsSatisfied();
    }
}
