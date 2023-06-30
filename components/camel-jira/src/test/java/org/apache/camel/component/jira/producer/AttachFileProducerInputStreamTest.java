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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.api.domain.Attachment;
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
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.camel.component.jira.JiraConstants.ISSUE_KEY;
import static org.apache.camel.component.jira.JiraConstants.JIRA;
import static org.apache.camel.component.jira.JiraConstants.JIRA_REST_CLIENT_FACTORY;
import static org.apache.camel.component.jira.JiraTestConstants.JIRA_CREDENTIALS;
import static org.apache.camel.component.jira.JiraTestConstants.KEY;
import static org.apache.camel.component.jira.Utils.createIssue;
import static org.apache.camel.component.jira.Utils.createIssueWithAttachment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AttachFileProducerInputStreamTest extends CamelTestSupport {

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

    private Issue issue;
    private String attachedName;
    private int attachedSize;

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind(JIRA_REST_CLIENT_FACTORY, jiraRestClientFactory);
    }

    public void setMocks() {
        when(jiraRestClientFactory.createWithBasicHttpAuthentication(any(), any(), any())).thenReturn(jiraClient);
        when(jiraClient.getIssueClient()).thenReturn(issueRestClient);

        when(issueRestClient.getIssue(any())).then(inv -> {
            if (issue == null) {
                issue = createIssue(1);
            }
            return Promises.promise(issue);
        });
        when(issueRestClient.addAttachment(any(URI.class), any(InputStream.class), any(String.class))).then(inv -> {
            InputStream is = inv.getArgument(1);
            attachedName = inv.getArgument(2);
            attachedSize = is.readAllBytes().length;

            Collection<Attachment> attachments = new ArrayList<>();
            attachments.add(new Attachment(
                    issue.getAttachmentsUri(), attachedName, null, null,
                    attachedSize, null, null, null));
            // re-create the issue with the attachment sent by the route
            issue = createIssueWithAttachment(issue.getId(), issue.getSummary(), issue.getKey(), issue.getIssueType(),
                    issue.getDescription(), issue.getPriority(), issue.getAssignee(), attachments);
            return null;
        });
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
                from("direct:start")
                        .setHeader(ISSUE_KEY, () -> KEY + "-1")
                        .to("jira://attach?jiraUrl=" + JIRA_CREDENTIALS)
                        .to(mockResult);
            }
        };
    }

    @Test
    public void verifyAttachment() throws InterruptedException, IOException {
        mockResult.expectedMessageCount(1);

        String text = "A random text to use on the AttachFileProducerTest.java of camel-jira component.";
        template.sendBody("direct:start", text);

        mockResult.assertIsSatisfied();

        Issue retrievedIssue = issueRestClient.getIssue(issue.getKey()).claim();
        assertEquals(issue, retrievedIssue);
        // there is only one attachment
        Attachment attachFile = retrievedIssue.getAttachments().iterator().next();
        assertEquals(attachFile.getFilename(), attachedName);
        assertEquals(attachFile.getSize(), attachedSize);
    }
}
