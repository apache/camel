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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.BasicWatchers;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.LinkIssuesInput;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import com.atlassian.jira.rest.client.api.domain.input.WorklogInput;
import io.atlassian.util.concurrent.Promise;
import io.atlassian.util.concurrent.Promises;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jira.JiraComponent;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.camel.component.jira.JiraConstants.CHILD_ISSUE_KEY;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_KEY;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_PROJECT_KEY;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_SUMMARY;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_TRANSITION_ID;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_TYPE_ID;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_WATCHERS_ADD;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_WATCHERS_REMOVE;
import static org.apache.camel.component.jira.JiraConstants.JIRA;
import static org.apache.camel.component.jira.JiraConstants.JIRA_REST_CLIENT_FACTORY;
import static org.apache.camel.component.jira.JiraConstants.LINK_TYPE;
import static org.apache.camel.component.jira.JiraConstants.MINUTES_SPENT;
import static org.apache.camel.component.jira.JiraConstants.PARENT_ISSUE_KEY;
import static org.apache.camel.component.jira.JiraTestConstants.JIRA_CREDENTIALS;
import static org.apache.camel.component.jira.JiraTestConstants.KEY;
import static org.apache.camel.component.jira.JiraTestConstants.TEST_JIRA_URL;
import static org.apache.camel.component.jira.Utils.createIssue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The Jira client is asynchronous: a request that Jira rejects (no permission, an invalid transition, a missing field)
 * fails its promise. The producers must wait for the promise, so that the exchange fails too.
 */
@ExtendWith(MockitoExtension.class)
class JiraProducerRejectedRequestTest extends CamelTestSupport {

    private static final String ISSUE = KEY + "-11";

    private final RestClientException rejection
            = new RestClientException("Jira rejected the request", new IllegalStateException("HTTP 400"));

    private Issue issue;

    @Mock
    private JiraRestClient jiraClient;
    @Mock
    private JiraRestClientFactory jiraRestClientFactory;
    @Mock
    private IssueRestClient issueRestClient;

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind(JIRA_REST_CLIENT_FACTORY, jiraRestClientFactory);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        URI watchersUri = URI.create(TEST_JIRA_URL + "/rest/api/2/issue/" + ISSUE + "/watchers");
        issue = createIssue(11L, "Test issue", ISSUE, null, null, null, null, null,
                new BasicWatchers(watchersUri, true, 0));
        Promise<Void> rejected = Promises.rejected(rejection);

        lenient().when(jiraRestClientFactory.createWithBasicHttpAuthentication(any(), any(), any())).thenReturn(jiraClient);
        lenient().when(jiraClient.getIssueClient()).thenReturn(issueRestClient);
        // reading and creating an issue work
        lenient().when(issueRestClient.getIssue(anyString())).thenReturn(Promises.promise(issue));
        lenient().when(issueRestClient.createIssue(any(IssueInput.class)))
                .thenReturn(Promises.promise(new BasicIssue(issue.getSelf(), ISSUE, 11L)));
        // every request that changes the issue is rejected
        lenient().when(issueRestClient.addComment(any(), any(Comment.class))).thenReturn(rejected);
        lenient().when(issueRestClient.linkIssue(any(LinkIssuesInput.class))).thenReturn(rejected);
        lenient().when(issueRestClient.addWorklog(any(), any(WorklogInput.class))).thenReturn(rejected);
        lenient().when(issueRestClient.addAttachments(any(), any(File.class))).thenReturn(rejected);
        lenient().when(issueRestClient.addAttachment(any(), any(InputStream.class), anyString())).thenReturn(rejected);
        lenient().when(issueRestClient.transition(any(Issue.class), any(TransitionInput.class))).thenReturn(rejected);
        lenient().when(issueRestClient.addWatcher(any(), anyString())).thenReturn(rejected);
        lenient().when(issueRestClient.removeWatcher(any(), anyString())).thenReturn(rejected);

        CamelContext camelContext = super.createCamelContext();
        camelContext.disableJMX();
        camelContext.addComponent(JIRA, new JiraComponent(camelContext));
        return camelContext;
    }

    @Test
    void addCommentFails() {
        assertRejected("addComment", "A comment", Map.of(ISSUE_KEY, ISSUE));
    }

    @Test
    void addIssueLinkFails() {
        assertRejected("addIssueLink", null,
                Map.of(PARENT_ISSUE_KEY, ISSUE, CHILD_ISSUE_KEY, KEY + "-12", LINK_TYPE, "Relates"));
    }

    @Test
    void addWorkLogFails() {
        assertRejected("addWorkLog", "A comment", Map.of(ISSUE_KEY, ISSUE, MINUTES_SPENT, 10));
    }

    @Test
    void attachAFileFails(@TempDir Path dir) throws Exception {
        File file = Files.writeString(dir.resolve("attachment.txt"), "content").toFile();

        assertRejected("attach", file, Map.of(ISSUE_KEY, ISSUE));
    }

    @Test
    void attachAStreamFails() {
        InputStream body = new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8));

        assertRejected("attach", body, Map.of(ISSUE_KEY, ISSUE, Exchange.FILE_NAME, "attachment.txt"));
    }

    @Test
    void transitionIssueFails() {
        assertRejected("transitionIssue", null, Map.of(ISSUE_KEY, ISSUE, ISSUE_TRANSITION_ID, 3));
    }

    @Test
    void transitionIssueFailsWhenItsCommentIsRejected() {
        // partial failure: Jira makes the transition and then rejects the comment (the message body)
        when(issueRestClient.transition(any(Issue.class), any(TransitionInput.class))).thenReturn(Promises.promise(null));

        assertRejected("transitionIssue", "A comment", Map.of(ISSUE_KEY, ISSUE, ISSUE_TRANSITION_ID, 3));

        // the transition was requested before the comment
        InOrder inOrder = inOrder(issueRestClient);
        inOrder.verify(issueRestClient).transition(same(issue), any(TransitionInput.class));
        inOrder.verify(issueRestClient).addComment(any(), any(Comment.class));
    }

    @Test
    void addWatcherFails() {
        assertRejected("watchers", null, Map.of(ISSUE_KEY, ISSUE, ISSUE_WATCHERS_ADD, "someone"));
    }

    @Test
    void removeWatcherFails() {
        assertRejected("watchers", null, Map.of(ISSUE_KEY, ISSUE, ISSUE_WATCHERS_REMOVE, "someone"));
    }

    @Test
    void addIssueWithAWatcherFails() {
        assertRejected("addIssue", "A description",
                Map.of(ISSUE_PROJECT_KEY, KEY, ISSUE_TYPE_ID, 1L, ISSUE_SUMMARY, "A summary", ISSUE_WATCHERS_ADD, "someone"));
    }

    @Test
    void addIssueKeepsTheCreatedIssueWhenAWatcherFails() {
        Exchange exchange = template.request("direct:addIssue", e -> {
            e.getIn().setBody("A description");
            e.getIn().setHeaders(new HashMap<>(
                    Map.of(ISSUE_PROJECT_KEY, KEY, ISSUE_TYPE_ID, 1L, ISSUE_SUMMARY, "A summary", ISSUE_WATCHERS_ADD,
                            "someone")));
        });

        assertSame(rejection, exchange.getException());
        // the issue exists in Jira, so the exchange must carry it for the error handling of the route
        assertSame(issue, exchange.getMessage().getBody());
    }

    private void assertRejected(String type, Object body, Map<String, Object> headers) {
        Exchange exchange = template.request("direct:" + type, e -> {
            e.getIn().setBody(body);
            e.getIn().setHeaders(new HashMap<>(headers));
        });

        assertSame(rejection, exchange.getException());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                for (String type : new String[] {
                        "addComment", "addIssueLink", "addWorkLog", "attach", "transitionIssue", "watchers", "addIssue" }) {
                    from("direct:" + type).to("jira://" + type + "?jiraUrl=" + JIRA_CREDENTIALS);
                }
            }
        };
    }
}
