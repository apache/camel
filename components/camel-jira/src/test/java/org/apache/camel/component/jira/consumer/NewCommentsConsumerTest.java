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
import java.util.concurrent.atomic.AtomicInteger;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Comment;
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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.apache.camel.component.jira.JiraConstants.JIRA;
import static org.apache.camel.component.jira.JiraConstants.JIRA_REST_CLIENT_FACTORY;
import static org.apache.camel.component.jira.JiraTestConstants.JIRA_CREDENTIALS;
import static org.apache.camel.component.jira.JiraTestConstants.PROJECT;
import static org.apache.camel.component.jira.Utils.createIssue;
import static org.apache.camel.component.jira.Utils.createIssueWithComments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NewCommentsConsumerTest extends CamelTestSupport {

    private static List<Issue> issues = new ArrayList<>();

    @Mock
    private JiraRestClient jiraClient;

    @Mock
    private JiraRestClientFactory jiraRestClientFactory;

    @Mock
    private SearchRestClient searchRestClient;

    @Mock
    private IssueRestClient issueRestClient;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;



    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind(JIRA_REST_CLIENT_FACTORY, jiraRestClientFactory);
    }

    @BeforeClass
    public static void beforeAll() {
        issues.add(createIssueWithComments(1L, 1));
        issues.add(createIssueWithComments(2L, 1));
        issues.add(createIssueWithComments(3L, 1));
    }

    public void setMocks() {
        SearchResult result = new SearchResult(0, 50, 100, issues);
        Promise<SearchResult> promiseSearchResult = Promises.promise(result);
        Issue issue = createIssueWithComments(4L, 1);
        Promise<Issue> promiseIssue = Promises.promise(issue);

        when(jiraClient.getSearchClient()).thenReturn(searchRestClient);
        when(jiraClient.getIssueClient()).thenReturn(issueRestClient);
        when(jiraRestClientFactory.createWithBasicHttpAuthentication(any(), any(), any())).thenReturn(jiraClient);
        when(searchRestClient.searchJql(any(), any(), any(), any())).thenReturn(promiseSearchResult);
        when(issueRestClient.getIssue(anyString())).thenReturn(promiseIssue);
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
                from("jira://newComments?jiraUrl=" + JIRA_CREDENTIALS + "&jql=project=" + PROJECT + "&delay=500")
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
    public void singleIssueCommentsTest() throws Exception {
        // create issue with 3 comments
        Issue issue = createIssueWithComments(11, 3);
        reset(issueRestClient);
        Promise<Issue> promiseIssue = Promises.promise(issue);
        when(issueRestClient.getIssue(anyString())).thenReturn(promiseIssue);
        List<Comment> comments = new ArrayList<>();
        for (Comment c: issue.getComments()) {
            comments.add(c);
        }
        // reverse the order, from oldest comment to recent
        Collections.reverse(comments);
        // expect 3 comments
        mockResult.expectedBodiesReceived(comments);
        mockResult.assertIsSatisfied();
    }

    @Test
    public void multipleIssuesTest() throws Exception {
        Issue issue1 = createIssueWithComments(20L, 2);
        Issue issue2 = createIssueWithComments(21L, 3);
        Issue issue3 = createIssueWithComments(22L, 1);
        List<Issue> newIssues = new ArrayList<>();
        newIssues.add(issue1);
        newIssues.add(issue2);
        newIssues.add(issue3);
        Issue issueWithNoComments = createIssue(31L);

        reset(searchRestClient);
        reset(issueRestClient);
        SearchResult searchResult = new SearchResult(0, 50, 3, newIssues);
        Promise<SearchResult> searchResultPromise = Promises.promise(searchResult);
        when(searchRestClient.searchJql(anyString(), any(), any(), any())).thenReturn(searchResultPromise);
        AtomicInteger regulator = new AtomicInteger(0);
        when(issueRestClient.getIssue(anyString())).then(inv -> {
            int idx = regulator.getAndIncrement();
            Issue issue = issueWithNoComments;
            if (idx < newIssues.size()) {
                issue = newIssues.get(idx);
            }
            return Promises.promise(issue);
        });
        List<Comment> comments = new ArrayList<>();
        for (Issue issue: newIssues) {
            for (Comment c: issue.getComments()) {
                comments.add(c);
            }
        }
        // reverse the order, from oldest comment to recent
        Collections.reverse(comments);
        // expect 6 comments
        mockResult.expectedBodiesReceived(comments);
        mockResult.assertIsSatisfied();
    }

}
