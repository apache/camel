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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The search for the latest issue at startup fails (for example the server answers 503 once): the issues that exist
 * when the consumer starts must not be routed as new issues, and the issues created later must be.
 */
@ExtendWith(MockitoExtension.class)
public class NewIssuesConsumerStartupLookupFailsTest extends CamelTestSupport {

    private static final List<Issue> EXISTING = List.of(
            createIssueWithCreationDate(3L, DateTime.now().minusMinutes(6)),
            createIssueWithCreationDate(2L, DateTime.now().minusMinutes(8)),
            createIssueWithCreationDate(1L, DateTime.now().minusMinutes(10)));
    private static final Issue NEW_ISSUE = createIssueWithCreationDate(4L, DateTime.now().plusMinutes(1));

    private final AtomicInteger latestIssueSearches = new AtomicInteger();
    private final AtomicInteger newIssueSearches = new AtomicInteger();
    private final CountDownLatch threeNewIssueSearches = new CountDownLatch(3);

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
        User user = new User(
                null, "admin", null, null, true, null,
                Map.of("48x48", URI.create("")), DateTime.now().getZone().getID());
        when(jiraClient.getSearchClient()).thenReturn(searchRestClient);
        when(jiraClient.getUserClient()).thenReturn(userRestClient);
        when(jiraRestClientFactory.createWithBasicHttpAuthentication(any(), any(), any())).thenReturn(jiraClient);
        when(userRestClient.getUser(any(URI.class))).thenReturn(Promises.promise(user));
        when(searchRestClient.searchJql(any(), any(), any(), any())).then(invocation -> {
            String jql = invocation.getArgument(0);
            List<Issue> issues;
            if (jql.startsWith("created > ")) {
                // the newer issues: one issue is created after the consumer started
                issues = newIssueSearches.getAndIncrement() == 0 ? List.of(NEW_ISSUE) : List.of();
                threeNewIssueSearches.countDown();
            } else if (jql.endsWith(" ORDER BY created DESC")) {
                // the latest issue: the first search fails
                if (latestIssueSearches.getAndIncrement() == 0) {
                    throw new IllegalStateException("Service Unavailable");
                }
                issues = EXISTING.subList(0, 1);
            } else {
                // all issues of the JQL, which all exist since before the consumer started
                issues = EXISTING;
            }
            return Promises.promise(new SearchResult(0, 50, issues.size(), issues));
        });

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
                from("jira://newIssues?jiraUrl=" + JIRA_CREDENTIALS + "&jql=project=" + PROJECT + "&delay=50")
                        .to(mockResult);
            }
        };
    }

    @Test
    public void existingIssuesAreNotRoutedWhenTheStartupLookupFailed() throws Exception {
        assertTrue(threeNewIssueSearches.await(20, TimeUnit.SECONDS), "the consumer must search for new issues");

        List<Object> bodies = mockResult.getReceivedExchanges().stream().map(e -> e.getMessage().getBody()).toList();
        assertEquals(List.of(NEW_ISSUE), bodies, "only the issue created after the start is new");
        assertEquals(2, latestIssueSearches.get(), "the latest issue is looked up again after the failed lookup");
    }
}
