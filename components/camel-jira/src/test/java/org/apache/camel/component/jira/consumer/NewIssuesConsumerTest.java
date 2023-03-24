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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import io.atlassian.util.concurrent.Promise;
import io.atlassian.util.concurrent.Promises;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jira.JiraComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.camel.component.jira.JiraConstants.JIRA;
import static org.apache.camel.component.jira.JiraConstants.JIRA_REST_CLIENT_FACTORY;
import static org.apache.camel.component.jira.JiraTestConstants.JIRA_CREDENTIALS;
import static org.apache.camel.component.jira.JiraTestConstants.PROJECT;
import static org.apache.camel.component.jira.Utils.createIssue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NewIssuesConsumerTest extends CamelTestSupport {

    private static final List<Issue> ISSUES = new ArrayList<>();

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

    @BeforeAll
    public static void beforeAll() {
        ISSUES.add(createIssue(3L));
        ISSUES.add(createIssue(2L));
        ISSUES.add(createIssue(1L));
    }

    public void setMocks() {
        SearchResult result = new SearchResult(0, 50, 3, ISSUES);
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
                from("jira://newIssues?jiraUrl=" + JIRA_CREDENTIALS + "&jql=project=" + PROJECT + "&delay=1000")
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
    public void singleIssueTest() throws Exception {
        Issue issue = createIssue(11);

        reset(searchRestClient);
        AtomicBoolean searched = new AtomicBoolean();
        when(searchRestClient.searchJql(any(), any(), any(), any())).then(invocation -> {
            List<Issue> newIssues = new ArrayList<>();
            if (!searched.get()) {
                newIssues.add(issue);
                searched.set(true);
            }
            SearchResult result = new SearchResult(0, 50, 100, newIssues);
            return Promises.promise(result);
        });
        mockResult.expectedBodiesReceived(issue);
        mockResult.assertIsSatisfied();
    }

    @Test
    public void multipleIssuesTest() throws Exception {
        Issue issue1 = createIssue(21);
        Issue issue2 = createIssue(22);
        Issue issue3 = createIssue(23);

        reset(searchRestClient);
        AtomicBoolean searched = new AtomicBoolean();
        when(searchRestClient.searchJql(any(), any(), any(), any())).then(invocation -> {
            List<Issue> newIssues = new ArrayList<>();
            if (!searched.get()) {
                newIssues.add(issue3);
                newIssues.add(issue2);
                newIssues.add(issue1);
                searched.set(true);
            }
            SearchResult result = new SearchResult(0, 50, 3, newIssues);
            return Promises.promise(result);
        });

        mockResult.expectedBodiesReceived(issue3, issue2, issue1);
        mockResult.assertIsSatisfied();
    }

    @Test
    public void multipleIssuesPaginationTest() throws Exception {
        Issue issue1 = createIssue(31);
        Issue issue2 = createIssue(32);
        Issue issue3 = createIssue(33);
        Issue issue4 = createIssue(34);
        Issue issue5 = createIssue(35);

        reset(searchRestClient);
        when(searchRestClient.searchJql(any(), any(), any(), any())).then(invocation -> {
            int startAt = invocation.getArgument(2);
            Assertions.assertEquals(0, startAt);

            // return getTotal=100 to force next page query
            SearchResult result = new SearchResult(0, 50, 100, List.of(issue5, issue4, issue3));
            return Promises.promise(result);
        }).then(invocation -> {
            int startAt = invocation.getArgument(2);
            Assertions.assertEquals(50, startAt);
            SearchResult result = new SearchResult(0, 50, 100, List.of(issue2, issue1));
            return Promises.promise(result);
        }).then(invocation -> {
            int startAt = invocation.getArgument(2);
            Assertions.assertEquals(100, startAt);
            SearchResult result = new SearchResult(0, 50, 0, Collections.emptyList());
            return Promises.promise(result);
        });

        mockResult.expectedBodiesReceived(issue5, issue4, issue3, issue2, issue1);
        mockResult.assertIsSatisfied();
    }

    @Test
    public void multipleIssuesAvoidDuplicatesTest() throws Exception {
        Issue issue1 = createIssue(41);
        Issue issue2 = createIssue(42);
        Issue issue3 = createIssue(43);

        reset(searchRestClient);
        when(searchRestClient.searchJql(any(), any(), any(), any())).then(invocation -> {
            // return getTotal=100 to force next page query
            SearchResult result = new SearchResult(0, 50, 100, List.of(issue3, issue2));
            return Promises.promise(result);
        }).then(invocation -> {
            SearchResult result = new SearchResult(0, 50, 100, List.of(issue3, issue2, issue1));
            return Promises.promise(result);
        }).then(invocation -> {
            SearchResult result = new SearchResult(0, 50, 0, Collections.emptyList());
            return Promises.promise(result);
        });

        mockResult.expectedBodiesReceived(issue3, issue2, issue1);
        mockResult.assertIsSatisfied();
    }

    @Test
    public void multipleQueriesOffsetFilterTest() throws Exception {
        Issue issue1 = createIssue(51);
        Issue issue2 = createIssue(52);
        Issue issue3 = createIssue(53);
        Issue issue4 = createIssue(54);

        reset(searchRestClient);
        when(searchRestClient.searchJql(any(), any(), any(), any())).then(invocation -> {
            SearchResult result = new SearchResult(0, 50, 3, List.of(issue3, issue2, issue1));
            return Promises.promise(result);
        }).then(invocation -> {
            int startAt = invocation.getArgument(2);
            Assertions.assertEquals(0, startAt);

            String jqlFilter = invocation.getArgument(0);
            Assertions.assertTrue(jqlFilter.startsWith("id > 53"));
            SearchResult result = new SearchResult(0, 50, 1, Collections.singletonList(issue4));
            return Promises.promise(result);
        });

        mockResult.expectedBodiesReceived(issue3, issue2, issue1);
        mockResult.assertIsSatisfied();

        mockResult.reset();

        mockResult.expectedBodiesReceived(issue4);
        mockResult.assertIsSatisfied();
    }
}
