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
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
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
 * The search for the latest issue fails at startup, and so does the first search for new issues. Once Jira answers, an
 * issue created after the consumer started (and so found as the latest issue by a later search for it) is routed, and
 * an issue created before the start, in the same minute (which the JQL returns too, as it has minutes only), is not.
 */
@ExtendWith(MockitoExtension.class)
public class NewIssuesConsumerCreatedSinceStartTest extends CamelTestSupport {

    // a time zone of the user far from the one of the JVM, so the JQL must use the minute of the start in that zone
    private static final DateTimeZone USER_ZONE = DateTimeZone.forID("Pacific/Kiritimati");

    // the issues the search returns once Jira answers, the newest first
    private final List<Issue> found = new CopyOnWriteArrayList<>();
    // the JQL of the searches for new issues
    private final List<String> newIssueQueries = new CopyOnWriteArrayList<>();
    private final AtomicInteger newIssueSearches = new AtomicInteger();
    private final CountDownLatch threeAnsweredSearches = new CountDownLatch(3);
    private volatile DateTime beforeStart;
    private volatile DateTime lookupFailed;

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
                Map.of("48x48", URI.create("")), USER_ZONE.getID());
        when(jiraClient.getSearchClient()).thenReturn(searchRestClient);
        when(jiraClient.getUserClient()).thenReturn(userRestClient);
        when(jiraRestClientFactory.createWithBasicHttpAuthentication(any(), any(), any())).thenReturn(jiraClient);
        when(userRestClient.getUser(any(URI.class))).thenReturn(Promises.promise(user));
        when(searchRestClient.searchJql(any(), any(), any(), any())).then(invocation -> {
            String jql = invocation.getArgument(0);
            if (!jql.startsWith("created > ")) {
                // the search for the latest issue
                if (lookupFailed != null) {
                    // a later search finds the issue created after the start as the latest one
                    return Promises.promise(new SearchResult(0, 1, 1, found.subList(0, 1)));
                }
                // it fails at startup
                lookupFailed = DateTime.now();
                // created after the start (which is before the failed lookup), before Jira answers again
                found.add(createIssueWithCreationDate(2L, lookupFailed.plusMillis(1)));
                // created before the start, in the same minute as far as the JQL is concerned
                found.add(createIssueWithCreationDate(1L, beforeStart.minusMillis(1)));
                throw new IllegalStateException("Service Unavailable");
            }
            newIssueQueries.add(jql);
            if (newIssueSearches.getAndIncrement() == 0) {
                // Jira still does not answer at the first poll
                throw new IllegalStateException("Service Unavailable");
            }
            threeAnsweredSearches.countDown();
            return Promises.promise(new SearchResult(0, 50, found.size(), found));
        });

        CamelContext camelContext = super.createCamelContext();
        camelContext.disableJMX();
        camelContext.addComponent(JIRA, new JiraComponent(camelContext));
        beforeStart = DateTime.now();
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
    public void issueCreatedAfterTheStartIsRoutedOnceJiraAnswers() throws Exception {
        assertTrue(threeAnsweredSearches.await(20, TimeUnit.SECONDS), "the consumer must search for new issues");

        List<Long> routed = mockResult.getReceivedExchanges().stream()
                .map(e -> e.getMessage().getBody(Issue.class).getId()).toList();
        assertEquals(List.of(2L), routed, "only the issue created after the start is new, and it is routed once");

        // the first searches ask for the issues created since the minute of the start, in the time zone of the user
        String query = newIssueQueries.get(0);
        String atStart = query.substring("created > \"".length(), query.indexOf('"', "created > \"".length()));
        List<String> startMinutes = List.of(
                beforeStart.withZone(USER_ZONE).toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm")),
                lookupFailed.withZone(USER_ZONE).toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm")));
        assertTrue(startMinutes.contains(atStart), "the JQL " + query + " must ask for one of " + startMinutes);
        assertEquals(query, newIssueQueries.get(1), "the poll after the failed one asks for the same issues");
    }
}
