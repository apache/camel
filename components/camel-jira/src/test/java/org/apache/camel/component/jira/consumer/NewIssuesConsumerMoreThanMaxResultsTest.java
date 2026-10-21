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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.UserRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.User;
import io.atlassian.util.concurrent.Promises;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jira.JiraComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.camel.component.jira.JiraConstants.JIRA;
import static org.apache.camel.component.jira.JiraConstants.JIRA_REST_CLIENT_FACTORY;
import static org.apache.camel.component.jira.JiraTestConstants.JIRA_CREDENTIALS;
import static org.apache.camel.component.jira.JiraTestConstants.PROJECT;
import static org.apache.camel.component.jira.Utils.createIssueWithCreationDate;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * More new issues than maxResults between two polls (a burst, or the polls after Jira was unreachable for a while):
 * every one of them must be consumed, not only the newest maxResults.
 */
@ExtendWith(MockitoExtension.class)
public class NewIssuesConsumerMoreThanMaxResultsTest extends CamelTestSupport {

    private static final int NEW_ISSUES = 60;

    private final DateTime start = DateTime.now().minusHours(2);
    private final Issue latestAtStart = createIssueWithCreationDate(1000, start);
    // newest first, as the JQL of the consumer orders them
    private final List<Issue> newIssues = new ArrayList<>();
    private final AtomicBoolean served = new AtomicBoolean();

    @Mock
    private JiraRestClient jiraClient;

    @Mock
    private JiraRestClientFactory jiraRestClientFactory;

    @Mock
    private SearchRestClient searchRestClient;

    @Mock
    private UserRestClient userRestClient;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind(JIRA_REST_CLIENT_FACTORY, jiraRestClientFactory);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        for (int i = NEW_ISSUES; i >= 1; i--) {
            newIssues.add(createIssueWithCreationDate(1000 + i, start.plusMinutes(i)));
        }
        User user = new User(
                null, "admin", null, null, true, null,
                Map.of("48x48", URI.create("")), DateTime.now().getZone().getID());
        SearchRestClient searchClient = searchClient();
        when(jiraClient.getSearchClient()).thenReturn(searchClient);
        when(jiraClient.getUserClient()).thenReturn(userRestClient);
        when(jiraRestClientFactory.createWithBasicHttpAuthentication(any(), any(), any())).thenReturn(jiraClient);
        when(userRestClient.getUser(any(URI.class))).thenReturn(Promises.promise(user));

        CamelContext camelContext = super.createCamelContext();
        camelContext.disableJMX();
        camelContext.addComponent(JIRA, new JiraComponent(camelContext));
        return camelContext;
    }

    /**
     * The search client of Jira Server / Data Center, which pages by startAt.
     */
    protected SearchRestClient searchClient() {
        when(searchRestClient.searchJql(any(), any(), any(), any())).then(invocation -> {
            String jql = invocation.getArgument(0);
            int maxPerQuery = invocation.getArgument(1);
            int startAt = invocation.getArgument(2);
            int total = total(jql);
            return Promises.promise(new SearchResult(startAt, maxPerQuery, total, page(jql, startAt, maxPerQuery)));
        });
        return searchRestClient;
    }

    /**
     * The number of issues the given search finds now.
     */
    protected int total(String jql) {
        if (!jql.startsWith("created >")) {
            return 1;
        }
        return served.get() ? 0 : NEW_ISSUES;
    }

    /**
     * The issues of the given page of the search: the latest issue at startup; then the issues created after it, all of
     * them at the first poll, which pages through them, and none at the next polls, as they are all older than the
     * newest one.
     */
    protected List<Issue> page(String jql, int startAt, int maxPerQuery) {
        if (!jql.startsWith("created >")) {
            return List.of(latestAtStart);
        }
        List<Issue> answer = new ArrayList<>();
        int total = total(jql);
        for (int i = startAt; i < Math.min(startAt + maxPerQuery, total); i++) {
            answer.add(newIssues.get(i));
        }
        if (startAt + maxPerQuery >= total) {
            served.set(true);
        }
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jira://newIssues?jiraUrl=" + JIRA_CREDENTIALS + "&jql=project=" + PROJECT + "&delay=500")
                        .to(mockResult);
            }
        };
    }

    @Test
    public void everyNewIssueIsConsumed() throws Exception {
        mockResult.expectedMessageCount(NEW_ISSUES);
        mockResult.expectedBodiesReceivedInAnyOrder(newIssues.toArray());
        mockResult.assertIsSatisfied();
    }
}
