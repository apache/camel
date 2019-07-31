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
package org.apache.camel.component.github.producer;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.github.GitHubComponent;
import org.apache.camel.component.github.GitHubComponentTestBase;
import org.apache.camel.component.github.GitHubConstants;
import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.Repository;
import org.junit.Test;

public class CreateIssueProducerTest extends GitHubComponentTestBase {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                context.addComponent("github", new GitHubComponent());
                from("direct:createIssue")
                        .process(new MockIssueCreateProducerProcessor())
                        .to("github://createissue?state=success&username=someguy&password=apassword&repoOwner=anotherguy&repoName=somerepo");
            } // end of configure


        };
    }

    @Test
    public void testCreateIssue() throws Exception {
        Repository repository = new Repository();

        Endpoint issueProducerEndpoint = getMandatoryEndpoint("direct:createIssue");
        Exchange exchange = issueProducerEndpoint.createExchange();
        String issueBody = "There's an error";
        exchange.getIn().setBody(issueBody);
        template.send(issueProducerEndpoint, exchange);

        Thread.sleep(1 * 1000);

        // Verify that the mock pull request service received this comment.
        Issue issue = issueService.getIssue(repository, 1);
        assertEquals("Error", issue.getTitle());
        assertEquals("There's an error", issue.getBody());
    }


    public class MockIssueCreateProducerProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Message in = exchange.getIn();
            Map<String, Object> headers = in.getHeaders();
            headers.put(GitHubConstants.GITHUB_ISSUE_TITLE, "Error");
        }
    }


}
