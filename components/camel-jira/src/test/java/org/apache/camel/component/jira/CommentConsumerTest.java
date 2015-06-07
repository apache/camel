/**
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
package org.apache.camel.component.jira;

import java.util.Date;

import com.atlassian.jira.rest.client.domain.BasicIssue;
import com.atlassian.jira.rest.client.domain.Comment;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jira.mocks.MockJerseyJiraRestClientFactory;
import org.apache.camel.component.jira.mocks.MockJiraRestClient;
import org.apache.camel.component.jira.mocks.MockSearchRestClient;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommentConsumerTest extends CamelTestSupport {

    public static final Logger LOG = LoggerFactory.getLogger(CommentConsumerTest.class);

    private static final String URL = "https://somerepo.atlassian.net";
    private static final String USERNAME = "someguy";
    private static final String PASSWORD = "xU3xjhay9yjEaZq";
    private static final String JIRA_CREDENTIALS = URL + "&username=" + USERNAME + "&password=" + PASSWORD;
    protected MockJerseyJiraRestClientFactory factory;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        factory = new MockJerseyJiraRestClientFactory();
        registry.bind("JerseyJiraRestClientFactory", factory);

        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jira://newComment?serverUrl=" + JIRA_CREDENTIALS
                        + "&jql=RAW(project=CAMELJIRA)" + "&delay=500")
                        .process(new NewCommentProcessor())
                        .to("mock:result");
            }
        };
    }
    
    @Test
    public void emptyAtStartupTest() throws Exception {
        MockEndpoint mockResultEndpoint = getMockEndpoint("mock:result");

        mockResultEndpoint.expectedMessageCount(0);
        
        mockResultEndpoint.assertIsSatisfied();
    }

    @Test
    public void singleIssueTest() throws Exception {
        MockEndpoint mockResultEndpoint = getMockEndpoint("mock:result");

        MockJiraRestClient jiraRestClient = factory.getClient();
        MockSearchRestClient searchRestClient = (MockSearchRestClient) jiraRestClient.getSearchClient();
        BasicIssue issue1 = searchRestClient.addIssue();
        String commentText = "Comment added at " + new Date();
        Comment comment1 = searchRestClient.addCommentToIssue(issue1, commentText);

        mockResultEndpoint.expectedBodiesReceived(comment1);

        mockResultEndpoint.assertIsSatisfied();
    }

    @Test
    public void multiIssueTest() throws Exception {
        MockEndpoint mockResultEndpoint = getMockEndpoint("mock:result");

        MockJiraRestClient jiraRestClient = factory.getClient();
        MockSearchRestClient searchRestClient = (MockSearchRestClient) jiraRestClient.getSearchClient();
        BasicIssue issue1 = searchRestClient.addIssue();
        Comment comment1 = searchRestClient.addCommentToIssue(issue1, "Comment added at " + new Date());
        BasicIssue issue2 = searchRestClient.addIssue();
        Comment comment2 = searchRestClient.addCommentToIssue(issue2, "Comment added at " + new Date());

        mockResultEndpoint.expectedBodiesReceivedInAnyOrder(comment1, comment2);

        mockResultEndpoint.assertIsSatisfied();
    }

    /**
     * Log new comments.  Not really needed for this test, but useful for debugging.
     */
    public class NewCommentProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Message in = exchange.getIn();
            Comment comment = (Comment) in.getBody();
            LOG.debug("Got comment with id " + comment.getId() + " Body " + comment.getBody());
        }
    }
}
