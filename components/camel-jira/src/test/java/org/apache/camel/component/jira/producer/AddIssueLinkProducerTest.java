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
package org.apache.camel.component.jira.producer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueLink;
import com.atlassian.jira.rest.client.api.domain.input.LinkIssuesInput;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jira.JiraComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import static org.apache.camel.component.jira.JiraConstants.CHILD_ISSUE_KEY;
import static org.apache.camel.component.jira.JiraConstants.JIRA;
import static org.apache.camel.component.jira.JiraConstants.JIRA_REST_CLIENT_FACTORY;
import static org.apache.camel.component.jira.JiraConstants.LINK_TYPE;
import static org.apache.camel.component.jira.JiraConstants.PARENT_ISSUE_KEY;
import static org.apache.camel.component.jira.JiraTestConstants.JIRA_CREDENTIALS;
import static org.apache.camel.component.jira.Utils.createIssue;
import static org.apache.camel.component.jira.Utils.createIssueWithLinks;
import static org.apache.camel.component.jira.Utils.newIssueLink;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AddIssueLinkProducerTest extends CamelTestSupport {

    @Mock
    private JiraRestClient jiraClient;

    @Mock
    private JiraRestClientFactory jiraRestClientFactory;

    @Mock
    private IssueRestClient issueRestClient;

    @Produce("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    private Issue parentIssue;
    private Issue childIssue;

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind(JIRA_REST_CLIENT_FACTORY, jiraRestClientFactory);
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

    public void setMocks() {
        when(jiraRestClientFactory.createWithBasicHttpAuthentication(any(), any(), any())).thenReturn(jiraClient);
        when(jiraClient.getIssueClient()).thenReturn(issueRestClient);

        parentIssue = createIssue(1);
        childIssue = createIssue(2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                    .to("jira://addIssueLink?jiraUrl=" + JIRA_CREDENTIALS)
                    .to(mockResult);
            }
        };
    }

    @Test
    public void testAddIssueLink() throws InterruptedException {
        String comment = "A new test comment " + new Date();
        String linkType = "Relates";
        Map<String, Object> headers = new HashMap<>();
        headers.put(PARENT_ISSUE_KEY, parentIssue.getKey());
        headers.put(CHILD_ISSUE_KEY, childIssue.getKey());
        headers.put(LINK_TYPE, linkType);

        when(issueRestClient.linkIssue(any(LinkIssuesInput.class)))
            .then((Answer<Void>) inv -> {
                Collection<IssueLink> links = new ArrayList<>();
                links.add(newIssueLink(childIssue.getId(), 1, comment));
                parentIssue = createIssueWithLinks(parentIssue.getId(), links);
                return null;
            });

        template.sendBodyAndHeaders(comment, headers);

        mockResult.expectedMessageCount(1);
        mockResult.assertIsSatisfied();

        verify(issueRestClient).linkIssue(any(LinkIssuesInput.class));
    }

    @Test
    public void testAddIssueLinkNoComment() throws InterruptedException {
        String linkType = "Relates";
        Map<String, Object> headers = new HashMap<>();
        headers.put(PARENT_ISSUE_KEY, parentIssue.getKey());
        headers.put(CHILD_ISSUE_KEY, childIssue.getKey());
        headers.put(LINK_TYPE, linkType);

        when(issueRestClient.linkIssue(any(LinkIssuesInput.class)))
            .then((Answer<Void>) inv -> {
                Collection<IssueLink> links = new ArrayList<>();
                links.add(newIssueLink(childIssue.getId(), 1, null));
                parentIssue = createIssueWithLinks(parentIssue.getId(), links);
                return null;
            });

        template.sendBodyAndHeaders(null, headers);

        mockResult.expectedMessageCount(1);
        mockResult.assertIsSatisfied();

        verify(issueRestClient).linkIssue(any(LinkIssuesInput.class));
    }

    @Test
    public void testAddIssueLinkMissingParentIssueKey() throws InterruptedException {
        String comment = "A new test comment " + new Date();
        String linkType = "Relates";
        Map<String, Object> headers = new HashMap<>();
        headers.put(CHILD_ISSUE_KEY, childIssue.getKey());
        headers.put(LINK_TYPE, linkType);

        try {
            template.sendBodyAndHeaders(comment, headers);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertStringContains(cause.getMessage(), PARENT_ISSUE_KEY);
        }

        mockResult.expectedMessageCount(0);
        mockResult.assertIsSatisfied();

        verify(issueRestClient, never()).linkIssue(any(LinkIssuesInput.class));
    }

    @Test
    public void testAddIssueLinkMissingChildIssueKey() throws InterruptedException {
        String comment = "A new test comment " + new Date();
        String linkType = "Relates";
        Map<String, Object> headers = new HashMap<>();
        headers.put(PARENT_ISSUE_KEY, parentIssue.getKey());
        headers.put(LINK_TYPE, linkType);

        try {
            template.sendBodyAndHeaders(comment, headers);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertStringContains(cause.getMessage(), CHILD_ISSUE_KEY);
        }

        mockResult.expectedMessageCount(0);
        mockResult.assertIsSatisfied();

        verify(issueRestClient, never()).linkIssue(any(LinkIssuesInput.class));
    }

    @Test
    public void testAddIssueLinkMissingLinkType() throws InterruptedException {
        String comment = "A new test comment " + new Date();
        Map<String, Object> headers = new HashMap<>();
        headers.put(PARENT_ISSUE_KEY, parentIssue.getKey());
        headers.put(CHILD_ISSUE_KEY, childIssue.getKey());

        try {
            template.sendBodyAndHeaders(comment, headers);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertStringContains(cause.getMessage(), LINK_TYPE);
        }

        mockResult.expectedMessageCount(0);
        mockResult.assertIsSatisfied();

        verify(issueRestClient, never()).linkIssue(any(LinkIssuesInput.class));
    }
}
