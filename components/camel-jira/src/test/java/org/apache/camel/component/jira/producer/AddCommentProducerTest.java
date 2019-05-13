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

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import io.atlassian.util.concurrent.Promises;
import org.apache.camel.CamelContext;
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

import static org.apache.camel.component.jira.JiraConstants.ISSUE_KEY;
import static org.apache.camel.component.jira.JiraConstants.JIRA;
import static org.apache.camel.component.jira.JiraConstants.JIRA_REST_CLIENT_FACTORY;
import static org.apache.camel.component.jira.JiraTestConstants.JIRA_CREDENTIALS;
import static org.apache.camel.component.jira.Utils.createIssueWithComments;
import static org.apache.camel.component.jira.Utils.newComment;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AddCommentProducerTest extends CamelTestSupport {

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

    private String comment;
    private Issue backendIssue;

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind(JIRA_REST_CLIENT_FACTORY, jiraRestClientFactory);
    }

    public void setMocks() {
        when(jiraRestClientFactory.createWithBasicHttpAuthentication(any(), any(), any())).thenReturn(jiraClient);
        when(jiraClient.getIssueClient()).thenReturn(issueRestClient);

        when(issueRestClient.addComment(any(), any())).then((Answer<Void>) inv -> {
            Collection<Comment> comments = new ArrayList<>();
            for (Comment c : backendIssue.getComments()) {
                comments.add(c);
            }
            comments.add(newComment(backendIssue.getId(), comments.size() + 1, comment));
            backendIssue = createIssueWithComments(backendIssue.getId(), comments);
            return null;
        });
        backendIssue = createIssueWithComments(1, 1);
        when(issueRestClient.getIssue(any())).then(inv -> Promises.promise(backendIssue));
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
                comment = "A new test comment " + new Date();
                from("direct:start")
                        .to("jira://addComment?jiraUrl=" + JIRA_CREDENTIALS)
                        .to(mockResult);
            }
        };
    }

    @Test
    public void verifyLastComment() throws InterruptedException {
        template.sendBodyAndHeader(comment, ISSUE_KEY, backendIssue.getKey());

        Issue issue = issueRestClient.getIssue(backendIssue.getKey()).claim();
        String lastComment = null;
        for (Comment c : issue.getComments()) {
            lastComment = c.getBody();
        }
        assertEquals(comment, lastComment);
        mockResult.expectedMessageCount(1);
        mockResult.assertIsSatisfied();
    }
}
