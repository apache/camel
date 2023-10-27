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
import java.util.StringJoiner;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.api.MetadataRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicComponent;
import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.BasicPriority;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueType;
import com.atlassian.jira.rest.client.api.domain.Priority;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import io.atlassian.util.concurrent.Promise;
import io.atlassian.util.concurrent.Promises;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jira.JiraComponent;
import org.apache.camel.component.jira.Utils;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.camel.component.jira.JiraConstants.ISSUE_KEY;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_PRIORITY_NAME;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_SUMMARY;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_TYPE_NAME;
import static org.apache.camel.component.jira.JiraConstants.JIRA;
import static org.apache.camel.component.jira.JiraConstants.JIRA_REST_CLIENT_FACTORY;
import static org.apache.camel.component.jira.JiraTestConstants.JIRA_CREDENTIALS;
import static org.apache.camel.component.jira.Utils.createIssue;
import static org.apache.camel.component.jira.Utils.userAssignee;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpdateIssueProducerTest extends CamelTestSupport {

    @Mock
    private JiraRestClient jiraClient;

    @Mock
    private JiraRestClientFactory jiraRestClientFactory;

    @Mock
    private IssueRestClient issueRestClient;

    @Mock
    private MetadataRestClient metadataRestClient;

    @Produce("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    private Issue backendIssue;

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind(JIRA_REST_CLIENT_FACTORY, jiraRestClientFactory);
    }

    public void setMocks() {
        when(jiraRestClientFactory.createWithBasicHttpAuthentication(any(), any(), any())).thenReturn(jiraClient);
        when(jiraClient.getIssueClient()).thenReturn(issueRestClient);
        when(jiraClient.getMetadataClient()).thenReturn(metadataRestClient);

        Map<Integer, IssueType> issueTypes = new HashMap<>();
        issueTypes.put(1, new IssueType(null, 1L, "Bug", false, null, null));
        issueTypes.put(2, new IssueType(null, 2L, "Task", false, null, null));
        Promise<Iterable<IssueType>> promiseIssueTypes = Promises.promise(issueTypes.values());
        when(metadataRestClient.getIssueTypes()).thenReturn(promiseIssueTypes);

        Map<Integer, Priority> issuePriorities = new HashMap<>();
        issuePriorities.put(1, new Priority(null, 1L, "High", null, null, null));
        issuePriorities.put(2, new Priority(null, 2L, "Low", null, null, null));
        Promise<Iterable<Priority>> promisePriorities = Promises.promise(issuePriorities.values());
        when(metadataRestClient.getPriorities()).thenReturn(promisePriorities);

        backendIssue = createIssue(11L);
        when(issueRestClient.updateIssue(anyString(), any(IssueInput.class))).then(inv -> {
            String issueKey = inv.getArgument(0);
            IssueInput issueInput = inv.getArgument(1);
            String summary = (String) issueInput.getField("summary").getValue();
            Integer issueTypeId = Integer.parseInt(getValue(issueInput, "issuetype", "id"));
            IssueType issueType = issueTypes.get(issueTypeId);
            String description = (String) issueInput.getField("description").getValue();
            Integer priorityId = Integer.parseInt(getValue(issueInput, "priority", "id"));
            BasicPriority priority = issuePriorities.get(priorityId);
            Collection<BasicComponent> components = new ArrayList<>(2);
            components.add(Utils.createBasicComponent(1L, "ux"));
            components.add(Utils.createBasicComponent(2L, "plugins"));
            backendIssue
                    = createIssue(11L, summary, issueKey, issueType, description, priority, userAssignee, components, null);
            BasicIssue basicIssue = new BasicIssue(backendIssue.getSelf(), backendIssue.getKey(), backendIssue.getId());
            return Promises.promise(basicIssue);
        });
        when(issueRestClient.getIssue(any())).then(inv -> Promises.promise(backendIssue));
    }

    private String getValue(IssueInput issueInput, String field, String key) {
        ComplexIssueInputFieldValue complexField = (ComplexIssueInputFieldValue) issueInput.getField(field).getValue();
        return (String) complexField.getValuesMap().get(key);
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
                        .to("jira://updateIssue?jiraUrl=" + JIRA_CREDENTIALS)
                        .to(mockResult);
            }
        };
    }

    @Test
    public void verifyIssueUpdated() throws InterruptedException {

        String type = "Bug";
        String priority = "High";
        String summary = "Demo Bug jira " + new Date();
        String description = "My jira description" + new Date();
        String components = "ux,plugins";

        Map<String, Object> headers = new HashMap<>();
        headers.put(ISSUE_KEY, backendIssue.getKey());
        headers.put(ISSUE_TYPE_NAME, type);
        headers.put(ISSUE_SUMMARY, summary);
        headers.put(ISSUE_PRIORITY_NAME, priority);

        template.sendBodyAndHeaders(description, headers);

        Issue issue = issueRestClient.getIssue(backendIssue.getKey()).claim();
        assertEquals(backendIssue, issue);
        assertEquals(type, issue.getIssueType().getName());
        assertEquals(priority, issue.getPriority().getName());
        assertEquals(summary, issue.getSummary());
        assertEquals(description, issue.getDescription());
        StringJoiner comps = new StringJoiner(",");
        issue.getComponents().forEach(c -> comps.add(c.getName()));
        assertEquals(comps.toString(), components);
        mockResult.expectedMessageCount(1);
        mockResult.assertIsSatisfied();
    }
}
